package com.gary.bilibili.user.service;

import com.gary.bilibili.user.dto.LoginDTO;
import com.gary.bilibili.user.dto.PasswordUpdateDTO;
import com.gary.bilibili.user.dto.ProfileUpdateDTO;
import com.gary.bilibili.user.dto.RegisterDTO;
import com.gary.bilibili.user.dto.SendCodeDTO;
import com.gary.bilibili.user.vo.CurrentUserVO;
import com.gary.bilibili.user.vo.LoginVO;
import com.gary.bilibili.user.vo.UserProfileVO;

public interface UserService {

    void sendCode(SendCodeDTO request);

    LoginVO register(RegisterDTO request);

    LoginVO login(LoginDTO request);

    void logout();

    CurrentUserVO getCurrentUser();

    void updateProfile(ProfileUpdateDTO request);

    void updatePassword(PasswordUpdateDTO request);

    UserProfileVO getProfile(Long userId);
}
