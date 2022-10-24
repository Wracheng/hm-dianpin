package com.hmdp;

import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;


/**
 * @author Mr.Wang
 * @version 1.0
 * @since 1.8
 */

@Slf4j
@SpringBootTest
public class Test {
    @org.junit.jupiter.api.Test
    public void testPhone(){
        boolean phoneInvalid = RegexUtils.isPhoneInvalid("18856239181");
        log.info(String.valueOf(phoneInvalid));
    }
}
