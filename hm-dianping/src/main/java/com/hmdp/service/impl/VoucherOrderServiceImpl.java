package com.hmdp.service.impl;

import cn.hutool.Hutool;
import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.*;
import com.rabbitmq.client.*;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.DELAY_QUEU;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private IVoucherOrderService iVoucherOrderService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RedisDelayQueueUtil redisDelayQueueUtil;



    private final static String QUEUE_NAME = "simple_queue";

    private static final DefaultRedisScript<Long> SECKILL;
    static {
        SECKILL=new DefaultRedisScript<>();
        SECKILL.setLocation(new ClassPathResource("stock.lua"));
        SECKILL.setResultType(Long.class);
    }

    private static final DefaultRedisScript<Long> ORDER_SECKILL;
    static {
        ORDER_SECKILL=new DefaultRedisScript<>();
        ORDER_SECKILL.setLocation(new ClassPathResource("mq.lua"));
        ORDER_SECKILL.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        LocalDateTime now = LocalDateTime.now();
        if(voucher.getBeginTime().compareTo(now) > 0 || voucher.getEndTime().compareTo(now) < 0){
            return Result.fail("秒杀未开始或已过期");
        }
        //判断库存
//        if(voucher.getStock() <= 0){
//            return Result.fail("库存不足");
//        }
        String s = stringRedisTemplate.opsForValue().get(SECKILL_STOCK_KEY + voucherId);
        if(Long.valueOf(s)<1){
            return Result.fail("库存不足1");
        }
        Long id = UserHolder.getUser().getId();
        //创建锁对象
//         SimpleRedisLock lock = new SimpleRedisLock("order" + id, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order" + id);
        //获取锁
        boolean b = lock.tryLock();
        if(!b){
            return Result.fail("不允许重复购买");
        }
        try {
            //获取代理对象，避免事务失效
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
            System.out.println("----------------没有异步1-->"+Thread.currentThread().getId()+"userID"+id);
            return iVoucherOrderService.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }


//    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Transactional
    public  Result createVoucherOrder(Long voucherId) {
        //一人一单
        Long id = UserHolder.getUser().getId();
        //toString会创建新对象

            int count = query().eq("user_id", id).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("用户已经购买过");
            }

            //减库存
//        seckillVoucherService.update().setSql("stock = stock-1").eq("voucher_id", voucherId).update();
//        Long increment = stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId, -1);
        Long execute = stringRedisTemplate.execute(SECKILL, Collections.singletonList(SECKILL_STOCK_KEY + voucherId));
            if (execute.intValue() != 0){
                return Result.fail("库存不足2");
            }

        //创建订单
                VoucherOrder voucherOrder = new VoucherOrder();
                long order = redisIdWorker.nestId("order");

                voucherOrder.setId(order);
                voucherOrder.setUserId(id);
                voucherOrder.setVoucherId(voucherId);
                seckillVoucherService.update().setSql("stock = stock-1").eq("voucher_id", voucherId).update();
                save(voucherOrder);
                System.out.println("----------------没有异步2-->"+Thread.currentThread().getId()+"userID"+id);

                //返回订单
                return Result.ok(order);
    }

    @Override
    public Result orderMq(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        LocalDateTime now = LocalDateTime.now();
        if(voucher.getBeginTime().compareTo(now) > 0 || voucher.getEndTime().compareTo(now) < 0){
            return Result.fail("秒杀未开始或已过期");
        }
        return iVoucherOrderService.createOrderMq(voucherId);

    }
    public Result createOrderMq(Long voucherId) {
        //用户Id
        Long userid = UserHolder.getUser().getId();
        //订单Id
        Long orderid = redisIdWorker.nestId("order");

        Long[] ids=new Long[]{voucherId,userid,orderid};
//        String tr_ids = Convert.toStr(ids);
        Long execute = stringRedisTemplate.execute(ORDER_SECKILL, Collections.emptyList(),voucherId.toString(),userid.toString());

        if (execute.intValue() == 1){
            return Result.fail("库存不足---mq");
        }else if(execute.intValue()  == 2){
            return Result.fail("不允许重复购买---mq");
        }
        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//
//        voucherOrder.setId(orderid);
//        voucherOrder.setUserId(userid);
//        voucherOrder.setVoucherId(voucherId);
//
//        save(voucherOrder);
        //发送消息到队列
        rabbitTemplate.convertAndSend(QUEUE_NAME,ids);
        System.out.println("-----------1>"+Thread.currentThread().getId());

        //返回订单
        return Result.ok(orderid);
    }

    // 4.1 使用线程池
    private static final ExecutorService EXECUTORSERVICE = Executors.newSingleThreadExecutor();


    @RabbitListener(queues = QUEUE_NAME)
    public void listenSimpleQueue(Long[] msg){
//        String[] ids = msg.split(",");
//        Long[] str2 = new Long[ids.length];
//        for (int i = 0; i < ids.length; i++) {
//            str2[i] = Long.valueOf(ids[i]);
//        }
        asyncCreateVoucherOrder(msg);
        System.out.println("-------------2>"+Thread.currentThread().getId());
    }

    public void sendMessage(Long[] arrays) throws Exception {
        // 1、获取到连接
        Connection connection = ConnectionUtil.getConnection();
        // 2、从连接中创建通道，使用通道才能完成消息相关的操作
        Channel channel = connection.createChannel();
        // 3、声明（创建）队列
        //参数：String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments
        /**
         * 参数明细
         * 1、queue 队列名称
         * 2、durable 是否持久化，如果持久化，mq重启后队列还在
         * 3、exclusive 是否独占连接，队列只允许在该连接中访问，如果connection连接关闭队列则自动删除,如果将此参数设置true可用于临时队列的创建
         * 4、autoDelete 自动删除，队列不再使用时是否自动删除此队列，如果将此参数和exclusive参数设置为true就可以实现临时队列（队列不用了就自动删除）
         * 5、arguments 参数，可以设置一个队列的扩展参数，比如：可设置存活时间
         */
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        // 4、消息内容
        String message = Convert.toStr(arrays);
        // 向指定的队列中发送消息
        //参数：String exchange, String routingKey, BasicProperties props, byte[] body
        /**
         * 参数明细：
         * 1、exchange，交换机，如果不指定将使用mq的默认交换机（设置为""）
         * 2、routingKey，路由key，交换机根据路由key来将消息转发到指定的队列，如果使用默认交换机，routingKey设置为队列的名称
         * 3、props，消息的属性
         * 4、body，消息内容
         */
        channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
        System.out.println(" [x] Sent '" + message + "'");

        //关闭通道和连接(资源关闭最好用try-catch-finally语句处理)
        channel.close();
        connection.close();
    }

    public void consumeMessage() throws Exception {
        // 获取到连接
        Connection connection = ConnectionUtil.getConnection();
        //创建会话通道,生产者和mq服务所有通信都在channel通道中完成
        Channel channel = connection.createChannel();
        // 声明队列
        //参数：String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments
        /**
         * 参数明细
         * 1、queue 队列名称
         * 2、durable 是否持久化，如果持久化，mq重启后队列还在
         * 3、exclusive 是否独占连接，队列只允许在该连接中访问，如果connection连接关闭队列则自动删除,如果将此参数设置true可用于临时队列的创建
         * 4、autoDelete 自动删除，队列不再使用时是否自动删除此队列，如果将此参数和exclusive参数设置为true就可以实现临时队列（队列不用了就自动删除）
         * 5、arguments 参数，可以设置一个队列的扩展参数，比如：可设置存活时间
         */
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        //实现消费方法
        DefaultConsumer consumer = new DefaultConsumer(channel) {
            // 获取消息，并且处理，这个方法类似事件监听，如果有消息的时候，会被自动调用

            /**
             * 当接收到消息后此方法将被调用
             * @param consumerTag  消费者标签，用来标识消费者的，在监听队列时设置channel.basicConsume
             * @param envelope 信封，通过envelope
             * @param properties 消息属性
             * @param body 消息内容
             * @throws IOException
             */
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                //交换机
                String exchange = envelope.getExchange();
                //消息id，mq在channel中用来标识消息的id，可用于确认消息已接收
                long deliveryTag = envelope.getDeliveryTag();
                // body 即消息体
                String msg = new String(body, "utf-8");
                Long[] ids = Convert.toLongArray(msg);
                // System.out.println(" [x] received : " + ids + "!");

                // 手动进行ACK
                /*
                 *  void basicAck(long deliveryTag, boolean multiple) throws IOException;
                 *  deliveryTag:用来标识消息的id
                 *  multiple：是否批量.true:将一次性ack所有小于deliveryTag的消息。
                 */
                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        };

        // 监听队列，第二个参数：是否自动进行消息确认。
        //参数：String queue, boolean autoAck, Consumer callback
        /**
         * 参数明细：
         * 1、queue 队列名称
         * 2、autoAck 自动回复，当消费者接收到消息后要告诉mq消息已接收，如果将此参数设置为tru表示会自动回复mq，如果设置为false要通过编程实现回复
         * 3、callback，消费方法，当消费者接收到消息要执行的方法
         */
        channel.basicConsume(QUEUE_NAME, false, consumer);

    }

    //真正下单业务
    @Transactional
    public void asyncCreateVoucherOrder(Long[] ids) {
//        创建订单
        Long voucherId=ids[0];
        Long userid=ids[1];
        Long orderid=ids[2];
        seckillVoucherService.update().setSql("stock = stock-1").eq("voucher_id", voucherId).update();
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderid);
        voucherOrder.setUserId(userid);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        redisDelayQueueUtil.addDelay(orderid);
        System.out.println("延迟");
    }



}
