# MAC修改助手

用于 Root Android 设备的 Wi-Fi / USB 网卡 MAC 修改工具，带可选的 LSPosed Wi-Fi 系统模块。

[下载 APK](https://github.com/liyongliao/mac-modify-assistant/releases/latest) · [使用说明](README.txt) · [AGPL-3.0](LICENSE)

## 功能

- **一键修改 Wi-Fi**：自动更新固定配置，再修改实际接口 MAC，并回读验证。
- **USB 网卡修改**：识别 USB 网络接口，使用 Root 修改 MAC；设备必须已枚举为网络接口。
- **地址库**：按名称保存 Wi-Fi / USB 地址，支持编辑、删除和快速应用。
- **四栏导航**：修改、地址库、模块、记录。悬浮底栏采用线条图标、文字标签和选中指示。
- **状态与诊断**：Root、当前开机的 Hook 状态、接口地址与驱动信息、本地日志导出。
- **恢复与校验**：修改前检查接口身份，失败尝试回滚，支持恢复同一接口实例修改前的地址。

## 环境要求

- Android 12 或更新版本（API 31+）。
- Root 权限，例如 KernelSU / SukiSU / Magisk。
- Wi-Fi 改址需要兼容的 Xposed 框架（例如 LSPosed），在“系统框架 / android”作用域启用本模块，然后重启。
- USB 改址只依赖 Root；是否支持取决于内核和网卡驱动。

## 使用

1. 安装 Release 中的 APK。在 Root 管理器中允许 **MAC修改助手**（`cn.macmanual`）。
2. 打开“模块”，检查 Root。使用 Wi-Fi 时，按界面说明启用 LSPosed 模块，并停用旧 MACsposed Fork，重启一次。
3. 在“修改”选择网卡、输入 MAC，点击“立即修改”并确认。Wi-Fi 固定配置会自动更新，无需另外保存配置。
4. 常用地址可“保存到地址库”；以后点击对应地址的“应用”即可，多个同类接口时会要求选择接口。
5. 若需要交还系统管理，在“模块”关闭 Wi-Fi 固定模式。关闭本身不会立即恢复出厂地址。

包名沿用 `cn.macmanual`，同签名的正式发布 APK 可以覆盖之前的“MAC 手动助手”，保留地址库与应用设置。地址库在本地私有空间保存，卸载会清除。

## 工作原理与边界

Wi-Fi 模块替换 `WifiVendorHal.setStaMacAddress` 的 MAC 参数，并保留原驱动调用和真实返回值；兼容时也替换 `WifiNative.getFactoryMacAddress` 的系统决策值。它只作用于指定的 Wi-Fi 客户端接口，不修改热点、蓝牙、固件或 EEPROM。

Root 端使用 `ip link` 修改接口，并读取 `/sys/class/net` 验证。操作会短暂中断网络。驱动拒绝、系统重新覆盖或 USB 设备未枚举时，会报告失败。失败回滚是尽力恢复，结果以实际回读为准。

本应用没有联网权限。USB 拔插后可能恢复默认地址；当前不提供开机或插入自动改址服务。

## 构建

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0，以及 `curl`、`zip`。设置 `JAVA_HOME` 和 `ANDROID_HOME` 后运行：

```sh
chmod +x build.sh
./build.sh
```

产物为 `dist/MAC-Manual.apk`。可设置 `OUTPUT_DIR` 指定输出目录。构建不依赖 Gradle；Xposed API 82 从官方 Maven 地址下载，仅作为编译依赖，不打入 APK。

首次本地构建会生成测试签名。仓库不包含发布私钥；自行构建的 APK 与 Release 签名不同，不能直接覆盖 Release 安装。请保管自己的签名文件。

## 测试

```sh
python3 tests/test_actions.py
mkdir -p build/tests
javac --release 8 -d build/tests src/cn/macmanual/MacRules.java tests/MacRulesTest.java
java -cp build/tests MacRulesTest
```

- 27 项隔离脚本测试：自动保存配置的执行顺序、Wi-Fi / USB 路径、接口身份校验、失败回滚等。
- 218 项 Java 地址格式和选择逻辑断言。
- `tests/android` 提供同签名 instrumentation 测试。`SmokeTest` 验证导航和地址库，不修改真实 MAC；`ApplyCheck` 会执行真实 Wi-Fi 切换与恢复，须明确提供测试 MAC。

1.1.0 已在 Root Android 15 设备上验证实际 Wi-Fi 改址和恢复；当时 setter Hook 有效，factoryDecision Hook 未加载。1.2.0 只更新应用名称和底栏，已完成构建、签名及主机测试；发布时设备未连接，未重复进行实机界面测试。USB 硬件改址尚未完成实机验证。

## 来源与许可

参考 [MACsposed](https://github.com/DavidBerdik/MACsposed) 和 [MACsposed-Fork](https://github.com/Samg381/MACsposed-Fork) 的 Wi-Fi Hook 思路，保留来源说明。项目以 **GNU AGPL v3.0** 发布，详见 [LICENSE](LICENSE) 和 [NOTICE.txt](NOTICE.txt)。
