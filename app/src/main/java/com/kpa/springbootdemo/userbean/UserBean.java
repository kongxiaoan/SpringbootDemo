package com.kpa.springbootdemo.userbean;

public class UserBean {
    private String userId;
    private String name;
    private String phone;
    private String age;
    private String sex;

    public UserBean() {
    }

    public UserBean(String userId, String name, String phone, String age, String sex) {
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.age = age;
        this.sex = sex;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }
}
