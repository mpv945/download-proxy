package org.ewancle.downloadproxy.utils;

import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;

import java.util.List;

public class UserAgentAnalyzerUtils {

    private static final int CACHE_SIZE = 1000;

    private static final List SUPPORTED_MOBILE_DEVICE_CLASSES = List.of("Mobile", "Tablet", "Phone");

    //@Bean // 注册到容器 public UserAgentAnalyzer userAgentAnalyzer()
    public static UserAgentAnalyzer userAgentAnalyzer() {
        return UserAgentAnalyzer
                .newBuilder()
                // UserAgentAnalyzer默认使用大小为10000的内存缓存，但我们可以根据需要使用withCache()方法更新缓存
                // 缓存有助于避免重复解析相同的用户代理字符串，从而提高性能
                .withCache(CACHE_SIZE)
                //.withAllFields()
                //.withFields(field1,field2......) //指定多个字段显示
                //.withField(field1).withField(field2)
                // ... other settings
                .build();
    }

    public static void main(String[] args) {
        //@GetMapping("/mobile/home")
        //public ModelAndView homePage(@RequestHeader(HttpHeaders.USER_AGENT) String userAgentString) {
        String USER_AGENT_STRING = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";
        //UserAgent userAgent = userAgentAnalyzer.parse(USER_AGENT_STRING);
        UserAgent userAgent = userAgentAnalyzer().parse(USER_AGENT_STRING);
        String deviceClass = userAgent.getValue(UserAgent.DEVICE_CLASS);
        boolean isMobileDevice = SUPPORTED_MOBILE_DEVICE_CLASSES.contains(deviceClass);
        System.out.println("isMobileDevice: "+isMobileDevice);
        userAgent
                .getAvailableFieldNamesSorted()
                .forEach(fieldName -> {
                    //log.info("{}: {}", fieldName, userAgent.getValue(fieldName));
                    System.out.println(fieldName+": "+userAgent.getValue(fieldName));
                });
    }
}
