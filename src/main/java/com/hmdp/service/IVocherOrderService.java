package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import org.springframework.stereotype.Service;


public interface IVocherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);
}
