package com.hmdp.utils;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * @program: hm-dianping
 * @description: RabbitMq连接
 * @author: 作者
 * @create: 2023-02-01 16:48
 */
public class ConnectionUtil {
    /**
     * 建立与RabbitMQ的连接
     * @return
     * @throws Exception
     */
    public static Connection getConnection() throws Exception {
        //定义连接工厂
        ConnectionFactory factory = new ConnectionFactory();
        //设置服务地址
        factory.setHost("192.168.76.200");
        //端口  15672是我们访问web时使用的，进行连接时应该使用5672
        factory.setPort(5672);
        //设置账号信息，用户名、密码、vhost
        factory.setVirtualHost("/");//设置虚拟机，一个mq服务可以设置多个虚拟机，每个虚拟机就相当于一个独立的mq
        factory.setUsername("root");
        factory.setPassword("aaaa");
        // 通过工厂获取连接
        Connection connection = factory.newConnection();
        return connection;
    }
}
