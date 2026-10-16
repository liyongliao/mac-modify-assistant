#!/system/bin/sh
# All arguments are validated, all sysfs paths are derived from discovered interfaces.
set -u
[ "$(id -u)" = 0 ] || { echo '需要 Root'; exit 10; }
op=${1:-}; iface=${2:-}; mac=${3:-}; expected_index=${4:-}; expected_path=${5:-}; expected_mac=${6:-}
case "$op" in
 profile-off)
  settings put global macmanual_v1_wifi_enabled 0 || exit 11
  [ "$(settings get global macmanual_v1_wifi_enabled)" = 0 ] || exit 11
  echo '已关闭 Wi-Fi 固定模式；下次连接交还系统管理。当前接口地址没有立即修改。'; exit 0;;
 apply|smart-apply|profile|reconnect) ;;
 *) echo '不支持的操作'; exit 12;;
esac
case "$iface" in ''|*[!a-zA-Z0-9_.-]*) echo '接口名无效'; exit 13;; esac
printf '%s\n' "$mac" | grep -Eq '^[0-9a-f]{2}(:[0-9a-f]{2}){5}$' || { echo 'MAC 格式无效'; exit 14; }
[ "$mac" != '00:00:00:00:00:00' ] && [ $((0x${mac%%:*} & 1)) -eq 0 ] || { echo '拒绝全零、组播或广播地址'; exit 15; }
case "$expected_index" in ''|*[!0-9]*) echo '接口索引无效'; exit 16;; esac
n="/sys/class/net/$iface"
identity() { [ -f "$n/ifindex" ] && [ "$(cat "$n/ifindex")" = "$expected_index" ] && [ "$(readlink -f "$n/device")" = "$expected_path" ]; }
identity || { echo '接口已移除或身份变化，请刷新'; exit 17; }
kind=''
case "$iface" in wlan[0-9]*) if [ -d "$n/phy80211" ] || [ -d "$n/wireless" ]; then kind=WIFI; fi;; esac
case "$expected_path" in */usb[0-9]*/*) kind=USB;; esac
[ -n "$kind" ] || { echo '仅支持真实 Wi-Fi 和 USB 网卡接口'; exit 18; }
if [ "$op" = profile ] || [ "$op" = reconnect ]; then
 [ "$kind" = WIFI ] || { echo '固定 Wi-Fi 设置只适用于 Wi-Fi'; exit 19; }
 if [ "$op" = profile ]; then
  settings put global macmanual_v1_wifi_enabled 0 || exit 20
  settings put global macmanual_v1_wifi_iface "$iface" || exit 20
  settings put global macmanual_v1_wifi_mac "$mac" || exit 20
  [ "$(settings get global macmanual_v1_wifi_iface)" = "$iface" ] && [ "$(settings get global macmanual_v1_wifi_mac)" = "$mac" ] || exit 20
  settings put global macmanual_v1_wifi_enabled 1 || exit 20
  [ "$(settings get global macmanual_v1_wifi_enabled)" = 1 ] || exit 20
  echo "已保存固定地址：$iface / ${mac}。需要本模块系统 Hook 在本次启动中生效。"; exit 0
 fi
 [ "$(settings get global macmanual_v1_hook_boot)" = "$(cat /proc/sys/kernel/random/boot_id)" ] || { echo '本次开机未检测到本模块 Hook，请在 LSPosed 启用并重启手机'; exit 21; }
 [ "$(settings get global macmanual_v1_wifi_enabled)" = 1 ] && [ "$(settings get global macmanual_v1_wifi_iface)" = "$iface" ] && [ "$(settings get global macmanual_v1_wifi_mac)" = "$mac" ] || { echo '请先保存当前固定地址'; exit 22; }
 [ "$(settings get global wifi_on)" = 1 ] || { echo '请先手动打开 Wi-Fi，再执行重连'; exit 23; }
 restore_wifi() { svc wifi enable; }
 trap restore_wifi EXIT HUP INT TERM
 svc wifi disable || exit 24
 sleep 2
 restore_wifi || exit 24
 trap - EXIT HUP INT TERM
 sleep 7
 current=$(cat "$n/address" 2>/dev/null)
 echo "CURRENT|$current"
 [ "$current" = "$mac" ] || { echo '重连后未读回目标地址，请检查驱动返回和旧模块冲突'; exit 25; }
 echo 'Wi-Fi 重连后已读回目标 MAC'; exit 0
fi
old=$(cat "$n/address")
[ "$old" = "$expected_mac" ] || { echo '当前地址已经变化，请刷新后重试'; exit 26; }
# The combined flow validates the current interface before changing persistent settings.
profile_changed=0
restore_profile() {
 [ "$profile_changed" = 1 ] || return 0
 settings put global macmanual_v1_wifi_enabled 0
 for key in wifi_iface wifi_mac wifi_enabled; do
  case "$key" in wifi_iface) val=$prior_iface;; wifi_mac) val=$prior_mac;; wifi_enabled) val=$prior_enabled;; esac
  if [ "$val" = null ]; then settings delete global "macmanual_v1_$key"; else settings put global "macmanual_v1_$key" "$val"; fi
 done
 echo '已尝试恢复修改前的 Wi-Fi 固定配置'
}
if [ "$op" = smart-apply ] && [ "$kind" = WIFI ]; then
 [ "$(settings get global macmanual_v1_hook_boot)" = "$(cat /proc/sys/kernel/random/boot_id)" ] || { echo '本次开机未检测到本模块 Hook；请在模块栏目完成启用并重启一次'; exit 21; }
 prior_enabled=$(settings get global macmanual_v1_wifi_enabled)
 prior_iface=$(settings get global macmanual_v1_wifi_iface)
 prior_mac=$(settings get global macmanual_v1_wifi_mac)
 profile_changed=1
 trap restore_profile EXIT HUP INT TERM
 settings put global macmanual_v1_wifi_enabled 0 || exit 20
 settings put global macmanual_v1_wifi_iface "$iface" || exit 20
 settings put global macmanual_v1_wifi_mac "$mac" || exit 20
 settings put global macmanual_v1_wifi_enabled 1 || exit 20
 [ "$(settings get global macmanual_v1_wifi_enabled)" = 1 ] && [ "$(settings get global macmanual_v1_wifi_iface)" = "$iface" ] && [ "$(settings get global macmanual_v1_wifi_mac)" = "$mac" ] || { echo '固定配置写入失败，尚未修改接口'; exit 20; }
 echo "已自动启用 Wi-Fi 固定地址：$iface / $mac"
fi
flags=$(cat "$n/flags")
[ -n "$flags" ] || exit 27
was_up=$((flags & 1))
echo "BEFORE|$iface|$expected_index|$expected_path|$old|$was_up"
[ "$mac" != "$old" ] || { echo "CURRENT|$old"; echo '当前已经是目标地址，无需修改'; trap - EXIT HUP INT TERM; exit 0; }
# Roll back MAC and restore link state on any failure or catchable interruption.
rollback() {
 restore_profile
 if identity; then
  ip link set dev "$iface" down
  ip link set dev "$iface" address "$old"
  if [ "$was_up" = 1 ]; then ip link set dev "$iface" up; fi
  echo "ROLLBACK|$(cat "$n/address" 2>/dev/null)"
 else echo '接口已消失或更换，跳过回滚，避免操作其他设备'; fi
}
trap rollback EXIT HUP INT TERM
identity || exit 17
ip link set dev "$iface" down || exit 28
ip link set dev "$iface" address "$mac" || { echo '驱动拒绝设置地址，正在尝试回滚'; exit 29; }
if [ "$was_up" = 1 ]; then ip link set dev "$iface" up || exit 30; fi
sleep 2
identity || exit 17
current=$(cat "$n/address")
echo "CURRENT|$current"
[ "$current" = "$mac" ] || { echo '读回地址与目标不一致，正在尝试回滚'; exit 31; }
trap - EXIT HUP INT TERM
echo '已修改实际接口 MAC，并完成回读验证。'
exit 0
