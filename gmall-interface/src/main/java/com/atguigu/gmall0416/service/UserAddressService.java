package com.atguigu.gmall0416.service;

import com.atguigu.gmall0416.bean.UserAddress;

import java.util.List;

public interface UserAddressService {
    List<UserAddress> findByUserId(String userId);
}
