package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> getTypeList() {
        /*
            1、尝试着从redis中查缓存
            2、查到了直接返回商铺信息
            3、没查到去数据库中查询
            4、数据库查到了返回店铺信息并且将店铺信息加入到redis中
            5、数据库也没查到返回错误信息
         */
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(key, 0, -1);

        // 如果redis中含有相关信息，则将其取出来依次添加进集合中并返回
        if(!shopTypeList.isEmpty()){
           List<ShopType> typeList = new ArrayList<>();
           for(String s : shopTypeList){
               ShopType shopType = JSONUtil.toBean(s, ShopType.class);
               typeList.add(shopType);
           }
           return typeList;
        }

        // redis 中没有，去数据库中查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if(typeList.isEmpty()){
            return null;
        }


        // 将数据库内容添加进redis中
        for (ShopType shopType : typeList) {
            String s = JSONUtil.toJsonStr(shopType);
            shopTypeList.add(s);
        }
        stringRedisTemplate.opsForList().rightPushAll(key,shopTypeList);
        return typeList;
    }
}
