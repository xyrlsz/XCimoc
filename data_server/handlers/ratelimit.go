package handlers

import (
	"sync"
	"time"
)

// 登录失败限流：按「用户名 | 客户端IP」维度统计连续失败次数，达到阈值后临时锁定；
// 登录成功即清零。纯内存、进程级实现（无外部依赖），用于抵御在线暴力破解。
//
// 说明：多实例部署时各实例独立计数；本服务面向个人/小规模自托管，进程级限流足够，
// 如未来多实例部署可替换为 Redis 等共享存储。
const (
	loginFailLimit  = 5               // 窗口内允许的连续失败次数
	loginFailWindow = 5 * time.Minute // 失败计数统计窗口
	loginBlockFor   = 5 * time.Minute // 触发阈值后的锁定时长
	loginGuardSweep = 1000            // 条目数超过该值时清理过期记录，防内存膨胀
)

type loginGuardState struct {
	fails        int       // 当前窗口内失败次数
	windowStart  time.Time // 当前统计窗口起点
	blockedUntil time.Time // 锁定截止时间（未锁定为零值）
}

var (
	loginGuardMu sync.Mutex
	loginGuard   = make(map[string]*loginGuardState)
)

// loginGuardAllow 判断 key 当前是否允许尝试登录；不允许时返回剩余等待时长。
func loginGuardAllow(key string) (bool, time.Duration) {
	now := time.Now()
	loginGuardMu.Lock()
	defer loginGuardMu.Unlock()
	if len(loginGuard) > loginGuardSweep {
		sweepLoginGuard(now)
	}
	st := loginGuard[key]
	if st == nil {
		return true, 0
	}
	if st.blockedUntil.After(now) {
		return false, st.blockedUntil.Sub(now)
	}
	if now.Sub(st.windowStart) > loginFailWindow {
		delete(loginGuard, key)
	}
	return true, 0
}

// loginGuardFail 记录一次登录失败；窗口内失败达到阈值则锁定。
func loginGuardFail(key string) {
	now := time.Now()
	loginGuardMu.Lock()
	defer loginGuardMu.Unlock()
	st := loginGuard[key]
	if st == nil || now.Sub(st.windowStart) > loginFailWindow {
		st = &loginGuardState{windowStart: now}
		loginGuard[key] = st
	}
	st.fails++
	if st.fails >= loginFailLimit {
		st.blockedUntil = now.Add(loginBlockFor)
	}
}

// loginGuardSuccess 登录成功：清除该 key 的失败记录。
func loginGuardSuccess(key string) {
	loginGuardMu.Lock()
	delete(loginGuard, key)
	loginGuardMu.Unlock()
}

// sweepLoginGuard 清理已过窗且未处于锁定状态的记录（调用方需持有 loginGuardMu）。
func sweepLoginGuard(now time.Time) {
	for k, st := range loginGuard {
		if st.blockedUntil.After(now) {
			continue
		}
		if now.Sub(st.windowStart) > loginFailWindow {
			delete(loginGuard, k)
		}
	}
}
