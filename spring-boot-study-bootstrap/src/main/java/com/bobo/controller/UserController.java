package com.bobo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @Value("${user.bobo.name}")
    private String userName;

    @Value("${user.bobo.age}")
    private Integer age;

    @GetMapping("hello")
    public String hello(){
        return "-->" + userName + " --" + age;
    }

}
