package com.project.model.service;

import com.project.library.JwtTokenProvider;
import com.project.library.SecurityUtil;
import com.project.model.dto.Response;
import com.project.model.dto.request.UserRequestDto;
import com.project.model.dto.response.UserResponseDto;
import com.project.model.dto.response.UserResponseDto.TokenInfo;
import com.project.model.entity.User;
import com.project.model.enums.Authority;
import com.project.model.repository.DiaryRepository;
import com.project.model.repository.UserQueryRepository;
import com.project.model.repository.UserRepository;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {
    
    
    private UserRepository               userRepository;
    private UserQueryRepository          userQueryRepository;
    private DiaryRepository              diaryRepository;
    private Response                     response;
    private PasswordEncoder              passwordEncoder;
    private JwtTokenProvider             jwtTokenProvider;
    private AuthenticationManagerBuilder authenticationManagerBuilder;
    private RedisTemplate                redisTemplate;
    private UserResponseDto              userResponseDto;
    
    @Autowired
    public UserService(UserRepository userRepository, UserQueryRepository userQueryRepository,
            DiaryRepository diaryRepository, Response response, PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider, AuthenticationManagerBuilder authenticationManagerBuilder,
            RedisTemplate redisTemplate, UserResponseDto userResponseDto) {
        this.userRepository               = userRepository;
        this.userQueryRepository          = userQueryRepository;
        this.diaryRepository              = diaryRepository;
        this.response                     = response;
        this.passwordEncoder              = passwordEncoder;
        this.jwtTokenProvider             = jwtTokenProvider;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.redisTemplate                = redisTemplate;
        this.userResponseDto              = userResponseDto;
    }
    
    public ResponseEntity<?> signUp(UserRequestDto.SignUp signUp) {
        return userQueryRepository.signUp(signUp);
    }
    
    public ResponseEntity<?> findAllUser() {
        return userQueryRepository.findAllUser();
    }
    
    public ResponseEntity<?> findUserByUserId(Long userId) {
        return userQueryRepository.findUserByUserId(userId);
    }
    
    public ResponseEntity<?> findUserByUserNickname(String userNickname) {
        return userQueryRepository.findUserByUserNickname(userNickname);
    }
    
    public ResponseEntity<?> deleteUser(UserRequestDto.Delete delete) {
        return userQueryRepository.deleteUser(delete);
    }
    
    /**
     * 로그인 (토큰 발급)
     *
     * @param login
     * @return response
     */
    public ResponseEntity<?> login(UserRequestDto.Login login) {
        
        if (userRepository.findUserByUserNickname(login.getUserNickname()).orElse(null) == null) {
            return response.fail("해당하는 유저가 존재하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        
        // 1. Login ID/PW 를 기반으로 Authentication 객체 생성
        // 이때 authentication 는 인증 여부를 확인하는 authenticated 값이 false
        UsernamePasswordAuthenticationToken authenticationToken = login.toAuthentication();
        
        // 2. 실제 검증 (사용자 비밀번호 체크)이 이루어지는 부분
        // authenticate 매서드가 실행될 때 CustomUserDetailsService 에서 만든 loadUserByUsername 메서드가 실행
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        
        // 3. 인증 정보를 기반으로 JWT 토큰 생성
        TokenInfo tokenInfo = jwtTokenProvider.generateToken(authentication);
        
        // 4. RefreshToken Redis 저장 (expirationTime 설정을 통해 자동 삭제 처리)
        redisTemplate.opsForValue().set("RT:" + authentication.getName(), tokenInfo.getRefreshToken(),
                tokenInfo.getRefreshTokenExpirationTime(), TimeUnit.MILLISECONDS);
        
        return response.success(tokenInfo, "로그인에 성공했습니다.", HttpStatus.OK);
    }
    
    /**
     * 토큰 재발급
     *
     * @param reissue
     * @return response
     */
    public ResponseEntity<?> reissue(UserRequestDto.Reissue reissue) {
        // 1. Refresh Token 검증
        if (!jwtTokenProvider.validateToken(reissue.getRefreshToken())) {
            return response.fail("Refresh Token 정보가 유효하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        
        // 2. Access Token 에서 User nickname 을 가져옵니다.
        Authentication authentication = jwtTokenProvider.getAuthentication(reissue.getAccessToken());
        
        // 3. Redis 에서 User nickname 을 기반으로 저장된 Refresh Token 값을 가져옵니다.
        String refreshToken = (String) redisTemplate.opsForValue().get("RT:" + authentication.getName());
        // (추가) 로그아웃되어 Redis 에 RefreshToken 이 존재하지 않는 경우 처리
        if (ObjectUtils.isEmpty(refreshToken)) {
            return response.fail("잘못된 요청입니다.", HttpStatus.BAD_REQUEST);
        }
        if (!refreshToken.equals(reissue.getRefreshToken())) {
            return response.fail("Refresh Token 정보가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        
        // 4. 새로운 토큰 생성
        TokenInfo tokenInfo = jwtTokenProvider.generateToken(authentication);
        
        // 5. RefreshToken Redis 업데이트
        redisTemplate.opsForValue().set("RT:" + authentication.getName(), tokenInfo.getRefreshToken(),
                tokenInfo.getRefreshTokenExpirationTime(), TimeUnit.MILLISECONDS);
        
        return response.success(tokenInfo, "Token 정보가 갱신되었습니다.", HttpStatus.OK);
    }
    
    /**
     * 로그아웃 (토큰 삭제)
     *
     * @param logout
     * @return response
     */
    public ResponseEntity<?> logout(UserRequestDto.Logout logout) {
        // (추가) 로그아웃되어 Redis 에 RefreshToken 이 존재하지 않는 경우 처리
        if (Boolean.TRUE.equals(redisTemplate.hasKey(logout.getAccessToken()))) {
            return response.fail("로그인된 계정이 아닙니다.", HttpStatus.BAD_REQUEST);
        }
        // 1. Access Token 검증
        if (!jwtTokenProvider.validateToken(logout.getAccessToken())) {
            return response.fail("잘못된 요청입니다.", HttpStatus.BAD_REQUEST);
        }
        
        // 2. Access Token 에서 User nickname 을 가져옵니다.
        Authentication authentication = jwtTokenProvider.getAuthentication(logout.getAccessToken());
        
        // 3. Redis 에서 해당 User nickname 로 저장된 Refresh Token 이 있는지 여부를 확인 후 있을 경우 삭제합니다.
        if (redisTemplate.opsForValue().get("RT:" + authentication.getName()) != null) {
            // Refresh Token 삭제
            redisTemplate.delete("RT:" + authentication.getName());
        }
        
        // 4. 해당 Access Token 유효시간 가지고 와서 BlackList 로 저장하기
        Long expiration = jwtTokenProvider.getExpiration(logout.getAccessToken());
        redisTemplate.opsForValue().set(logout.getAccessToken(), "logout", expiration, TimeUnit.MILLISECONDS);
        
        return response.success("로그아웃 되었습니다.");
    }
    
    /**
     * 관리자 권한 부여
     *
     * @return response
     */
    public ResponseEntity<?> authority() {
        // SecurityContext에 담겨 있는 authentication userNickname 정보
        String nickname = SecurityUtil.getCurrentUserNickname();
        
        User user = userRepository.findUserByUserNickname(nickname)
                .orElseThrow(() -> new UsernameNotFoundException("No authentication information."));
        
        // add ROLE_ADMIN
        user.getRoles().add(Authority.ROLE_ADMIN.name());
        userRepository.save(user);
        
        return response.success();
    }
}
