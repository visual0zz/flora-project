package com.flora.root.java.converter;

import java.util.List;

/**
 * BeanConverter / MapConverter 测试共用的样例 Bean（非测试类，仅提供夹具）。
 */
public final class BeanSamples {

    private BeanSamples() {
    }

    public static final class Address {
        private String city;
        private String zip;

        public Address() {
        }

        public Address(String city, String zip) {
            this.city = city;
            this.zip = zip;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getZip() {
            return zip;
        }

        public void setZip(String zip) {
            this.zip = zip;
        }
    }

    public static final class Person {
        private String name;
        private int age;
        private Address address;
        private List<Address> addresses;
        private Person self;

        public Person() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        public List<Address> getAddresses() {
            return addresses;
        }

        public void setAddresses(List<Address> addresses) {
            this.addresses = addresses;
        }

        public Person getSelf() {
            return self;
        }

        public void setSelf(Person self) {
            this.self = self;
        }
    }

    public record Point(int x, int y) {
    }

    /**
     * 无无参构造器且非 record：isBeanType 应判定为非 Bean（目标匹配器拒绝）。
     */
    public static final class NoDefaultCtor {
        private final String value;

        public NoDefaultCtor(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
