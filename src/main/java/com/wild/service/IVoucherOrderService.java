package com.wild.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wild.dto.Result;
import com.wild.entity.VoucherOrder;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {


    Result seckillVoucher(Long voucherId);
}
