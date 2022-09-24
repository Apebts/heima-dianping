package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

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
            return Result.ok(shop);
        }
        // 3、没查到去数据库中查询
        Shop shop = this.getById(id);
        // 4、数据库也没查到返回错误信息
        if(shop == null){
            return Result.fail("没找到店铺信息");
        }
        // 5、数据库查到了返回店铺信息并且将店铺信息加入到redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}
