package com.mmh.demo.controller;

import com.mmh.annotation.Autowired;
import com.mmh.annotation.Controller;
import com.mmh.annotation.RequestMapping;
import com.mmh.annotation.RequestParam;
import com.mmh.demo.service.IUserService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/")
public class UserController {

    @Autowired
    private IUserService userService;

    @RequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp,@RequestParam("name") String name){
        try {
            resp.getWriter().write(userService.query(name));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping("/add")
    public String add(@RequestParam("a") Integer a,@RequestParam("b") Integer b){
        return a +"+"+b+"="+(a+b);
    }

    @RequestMapping("/sub")
    public String sub(@RequestParam("a") Double a,@RequestParam("b") Double b){
        return a +"-"+b+"="+(a-b);
    }

}
