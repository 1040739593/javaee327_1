package cn.itcast.core.service;

import cn.itcast.common.utils.IdWorker;
import cn.itcast.core.dao.item.ItemDao;
import cn.itcast.core.dao.log.PayLogDao;
import cn.itcast.core.dao.order.OrderDao;
import cn.itcast.core.dao.order.OrderItemDao;
import cn.itcast.core.pojo.Cart;
import cn.itcast.core.pojo.item.Item;
import cn.itcast.core.pojo.log.PayLog;
import cn.itcast.core.pojo.order.Order;
import cn.itcast.core.pojo.order.OrderItem;
import com.alibaba.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderDao orderDao;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private IdWorker idWorker;
    @Autowired
    private ItemDao itemDao;
    @Autowired
    private OrderItemDao orderItemDao;
    @Autowired
    private PayLogDao payLogDao;

    //保存订单主表  订单详情表
    @Override
    public void add(Order order) {

        //支付总金额
        double tp=0;
        //订单ID集合
        List<String>ids=new ArrayList<>();
        List<Cart> cartList = (List<Cart>) redisTemplate.boundHashOps("CART").get(order.getUserId());
        for (Cart cart : cartList) {
            //订单ID   分布式ID生成器
            long id = idWorker.nextId();
            ids.add(String.valueOf(id));
            order.setOrderId(id);

            //实付金额
            double totalPrice=0;
            //状态
            order.setStatus("1");
            //创建时间
            order.setCreateTime(new Date());
            //更新时间
            order.setUpdateTime(new Date());
            //来源
            order.setSourceType("2");
            //商家ID
            order.setSellerId(cart.getSellerId());

            //保存订单详情
            List<OrderItem> orderItemList = cart.getOrderItemList();
            for (OrderItem orderItem : orderItemList) {
                //库存ID   数量
                Item item = itemDao.selectByPrimaryKey(orderItem.getItemId());
                //ID
                long orderItemId = idWorker.nextId();
                orderItem.setId(orderItemId);
                //商品ID
                orderItem.setGoodsId(item.getGoodsId());
                //订单ID
                orderItem.setOrderId(id);
                //标题
                orderItem.setTitle(item.getTitle());
                //图片
                orderItem.setPicPath(item.getImage());
                //商家ID
                orderItem.setSellerId(item.getSellerId());
                //单价
                orderItem.setPrice(item.getPrice());
                //小计
                orderItem.setTotalFee(new BigDecimal(item.getPrice().doubleValue()*orderItem.getNum()));
                totalPrice += orderItem.getTotalFee().doubleValue();

                //保存订单详情
                orderItemDao.insertSelective(orderItem);
            }
            //实付金额
            order.setPayment(new BigDecimal(totalPrice));
            tp += order.getPayment().doubleValue();

            //保存订单
            orderDao.insertSelective(order);
        }
        //删除
        redisTemplate.boundHashOps("CART").delete(order.getUserId());
        //几张订单 二张  千度  品优购
        //        //付款 一次  合并
        //        //银行流水
        //        //支付日志表
        //        //订单的ID == 支付ID  总金额  银行流水
        PayLog payLog=new PayLog();
        //支付订单ID
        long outTradeNo = idWorker.nextId();
        payLog.setOutTradeNo(String.valueOf(outTradeNo));
        //创建时间
        payLog.setCreateTime(new Date());
        //用户ID
        payLog.setUserId(order.getUserId());
        //支付状态 0  1
        payLog.setTradeState("0");
        //总金额 10.53元=1053分
        payLog.setTotalFee((long)(tp*100));
        //订单集合 [986096985763217408, 986096985784188928]  List<String>
        payLog.setOrderList(ids.toString().replace("[","").replace("]",""));
        //在线
        payLog.setPayType("1");

        payLogDao.insertSelective(payLog);
        redisTemplate.boundHashOps("payLog").put(order.getUserId(),payLog);


    }
}
