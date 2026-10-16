#!/system/bin/sh
[ "$(id -u)" = 0 ] && echo 'ROOT|yes' || echo 'ROOT|no'
echo "BOOT|$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
for key in wifi_enabled wifi_iface wifi_mac hook_boot hook_info last_result; do
 value=$(settings get global "macmanual_v1_$key" 2>/dev/null | tr '|\n' '  ')
 echo "CFG|$key|$value"
done
for n in /sys/class/net/*; do
 [ -f "$n/ifindex" ] || continue
 name=${n##*/}; path=$(readlink -f "$n/device" 2>/dev/null); kind=''
 case "$name" in wlan[0-9]*) [ -d "$n/phy80211" ] || [ -d "$n/wireless" ] || continue; kind=WIFI;; esac
 case "$path" in */usb[0-9]*/*) kind=USB;; esac
 [ -n "$kind" ] || continue
 mac=$(cat "$n/address" 2>/dev/null); idx=$(cat "$n/ifindex" 2>/dev/null)
 state=$(cat "$n/operstate" 2>/dev/null); flags=$(cat "$n/flags" 2>/dev/null)
 driver=$(readlink "$n/device/driver" 2>/dev/null); driver=${driver##*/}
 label=''; parent="$path"
 while [ -n "$parent" ] && [ "$parent" != / ]; do
  if [ -f "$parent/idVendor" ]; then
   label="$(cat "$parent/idVendor"):$(cat "$parent/idProduct") $(cat "$parent/product" 2>/dev/null | tr '|\n' '  ')"; break
  fi
  parent=${parent%/*}
 done
 echo "IFACE|$kind|$name|$mac|$idx|$path|$state|$flags|$driver|$label"
done
exit 0
