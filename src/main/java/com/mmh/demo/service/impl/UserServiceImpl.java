package com.mmh.demo.service.impl;

import com.mmh.annotation.Service;
import com.mmh.demo.service.IUserService;

@Service
public class UserServiceImpl implements IUserService {

    public String query(String name) {
        return "hello,"+name;
    }
}
