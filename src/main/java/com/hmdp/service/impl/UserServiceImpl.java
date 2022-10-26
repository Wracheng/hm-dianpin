package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 获取到手机号去做校验符不符合手机号的规范，肯定包括判空了
        // 校验手机号是否不符合规范
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (!phoneInvalid) {
            // 随机生成一个验证码
            String s = RandomUtil.randomNumbers(4);
            System.out.println("验证码为" + s);
            // 保存验证码到session
            // session.setAttribute("code",s);
            // 保存验证码到redis
            stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, s, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

            // 发送验证码给手机
            log.debug("验证码发送成功" + s);
            return Result.ok("发送成功");
        } else {
            // 如果不符合规范，提示手机不符合规范
            return Result.fail("手机号未填或不符合规范");
        }
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        // 判断是何种登录方式？手机验证码 or 手机号密码
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid) {
            return Result.fail("手机号不正确");
        }
        if (loginForm.getCode() != null) {
            // 验证码方式登录
            // String code = (String) session.getAttribute("code");
            String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
            if (!Objects.equals(code, loginForm.getCode())) {
                return Result.fail("code错误");
            }
            // 数据库去查有没有这个用户
            User user = query().eq("phone", phone).one();
            if (user == null) {
                // 创建一个用户
                user = createUserByPhone(phone);

            }
            // 将User对象脱敏，只返回一些不敏感信息
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            // 把用户信息保存在session中
            // session.setAttribute("userInfo",userDTO);
            // 把用户信息保存在redis中
            String token = UUID.randomUUID().toString(true);// true去掉-
            // userDTO转成的userDTOtMap里面有一个long类型stringRedisTemplate只能是String类型，所以需要特殊处理 ; setIgnoreNullValue(true) 有值就有值，无值就用原来的值
            Map<String, Object> userDTOtMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((key,value) -> value.toString()));
            stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userDTOtMap);
            stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
            return Result.ok(token);
        } else if (loginForm.getPassword() != null) {
            // 密码方式登录
        } else {
            return Result.fail("未填手机号或密码");
        }
        return Result.fail("登陆失败");
    }

    private User createUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        save(user);
        return user;
    }
}
