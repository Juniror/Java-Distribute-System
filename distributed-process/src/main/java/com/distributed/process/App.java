package com.distributed.process;

import redis.clients.jedis.Jedis;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        Jedis jedis = new Jedis("localhost", 6379);
        System.out.println(jedis.ping());
    }
}
