package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.aop.ThrowsAdvice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }


    /**
     * 使用互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        /*
            根据id从redis中查取，判断是否命中
               如果命中则直接返回数据
               如果没有命中，那么尝试获取互斥锁
                   如果没有获取到互斥锁，就代表之前已经有人获取过了，休眠一会，然后再次从redis中查询数据
                   如果获取到了互斥锁，那就根据id从数据库中查询数据，
                       查询到数据，将数据写入redis中，方便其他休眠结束的线程获取数据，并且释放掉互斥锁并返回
                       没查到数据就该报错了
         */
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopJson)) {
            // 如果获取到的是真实数据，那就返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if(shopJson != null){
            /*
                如果获取到的是空值，那就是这个id是不存在的，避免被大量不存在的id攻击，
                因此将这个id值 对应的value设置为null，避免直接访问数据库
             */
            return null;
        }

        /*
            没有从redis中获取到数据，那就去数据库中查找，
            但是为了避免过量访问数据库，因此用互斥锁的思想，只允许一个线程进入数据库
         */
        String lockKey = "lock:shop:"+id;
        Shop shop = null;
        try {
            boolean flag = tryLock(lockKey);
            if(!flag){
                /*
                    没获取到锁，那就休眠一会，醒来之后从头开始，继续开始从redis缓存中查找数据
                 */
                Thread.sleep(40);
                return queryWithMutex(id);
            }
            /*
                获取锁成功有两种情况：
                    1.之前没获取，休眠之后获取到了锁，但是这个锁是别人重建redis缓存用过的，
                    因此没必要再次获取锁了，首先判断redis缓存中有没有缓存，如果有缓存，那就是别人用过的锁，
                    不需要用了
                    2.第一个人用的锁，那么这个人负责查询数据库并完成redis缓存的重建
             */
            shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if(!StrUtil.isBlank(shopJson)) {
                return JSONUtil.toBean(shopJson,Shop.class);
            }

            // 下面这个是代表第一次获取锁，需要查询数据库和重建redis
            shop = this.getById(id);
            Thread.sleep(200);
            if (shop == null) {
                // 将空值写入redis，防止被恶意访问数据库
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",2,TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 释放互斥锁
            unLock(lockKey);
        }

        return shop;
    }


    /**
     * 解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
                /*
            1、尝试着从redis中查缓存
            2、查到了直接返回商铺信息
            3、没查到去数据库中查询
            4、数据库查到了返回店铺信息并且将店铺信息加入到redis中
            5、数据库也没查到返回错误信息
         */
        // 1、从redis中查缓存
        // 2、查到了直接返回商铺信息
        String jsonShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(!StrUtil.isBlank(jsonShop)){
            Shop shop = JSONUtil.toBean(jsonShop, Shop.class);
            return shop;
        }
        // 判断命中的是否是空值
        if(jsonShop != null){
            return null;
        }

        // 3、没查到去数据库中查询
        Shop shop = this.getById(id);
        // 4、数据库也没查到返回错误信息
        if(shop == null){
            // 将空值写入reids，并且为其设置比较短的有效期
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        // 5、数据库查到了返回店铺信息并且将店铺信息加入到redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL,TimeUnit.MINUTES);
        return shop;
    }
    // 获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id,Long expireSeconds){
        // 查询店铺数据
        Shop shop = getById(id);

        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }


    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        /*
            实现店铺的更新，且为店铺信息添加缓存，注意一定是先修改数据库，再将缓存信息删除
         */
        // 1、首先查询并更新数据库
        updateById(shop);

        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        // 2、删除缓存信息
        stringRedisTemplate.opsForHash().delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }

    /**
     * 使用逻辑过期解决缓存击穿
     * @param id
     * @return
     */
    public Shop test1(Long id){
        String jsonShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(!StrUtil.isBlank(jsonShop)){
            Shop shop = JSONUtil.toBean(jsonShop, Shop.class);
            return shop;
        }
        RedisData redisData = JSONUtil.toBean(jsonShop, RedisData.class);
        Shop data = (Shop) redisData.getData();

        return null;
    }

}
