package com.gary.bilibili.user.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.user.dto.LoginDTO;
import com.gary.bilibili.user.dto.PasswordUpdateDTO;
import com.gary.bilibili.user.dto.ProfileUpdateDTO;
import com.gary.bilibili.user.dto.RegisterDTO;
import com.gary.bilibili.user.dto.SendCodeDTO;
import com.gary.bilibili.user.service.UserService;
import com.gary.bilibili.user.vo.CurrentUserVO;
import com.gary.bilibili.user.vo.LoginVO;
import com.gary.bilibili.user.vo.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/user", "/api/user"})
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping({"/code", "/sendcode", "/sendCode"})
    public Result<Void> sendCode(@Valid @RequestBody SendCodeDTO request) {
        userService.sendCode(request);
        return Result.ok();
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO request) {
        return Result.ok(userService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO request) {
        return Result.ok(userService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<CurrentUserVO> me() {
        return Result.ok(userService.getCurrentUser());
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody ProfileUpdateDTO request) {
        userService.updateProfile(request);
        return Result.ok();
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody PasswordUpdateDTO request) {
        userService.updatePassword(request);
        return Result.ok();
    }

    @GetMapping("/profile/{userId}")
    public Result<UserProfileVO> getProfile(@PathVariable Long userId) {
        return Result.ok(userService.getProfile(userId));
    }
}
