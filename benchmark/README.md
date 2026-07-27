# Benchmark

本模块是应用级性能自动化测试的入口，使用 AndroidX Macrobenchmark，在独立进程中测量接近
release 配置的 `:app` benchmark 变体。

## 添加用例

在 `src/main/kotlin/indi/dmzz_yyhyy/lightnovelreader/benchmark/` 下添加测试类。建议按测试目的拆分：

- 启动性能：`StartupBenchmark`
- 页面滚动与卡顿：`ScrollBenchmark`
- 关键用户路径：按业务功能命名

测试类使用 `AndroidJUnit4`、`@LargeTest` 和 `MacrobenchmarkRule`。不要依赖应用内部实现，
跨进程交互统一通过 Macrobenchmark scope 和 UI Automator 完成。

## 运行

从仓库根目录执行：

```powershell
.\scripts\benchmark.ps1 doctor
.\scripts\benchmark.ps1 build
.\scripts\benchmark.ps1 run
```

只运行一个类或方法：

```powershell
.\scripts\benchmark.ps1 run -TestClass `
  "indi.dmzz_yyhyy.lightnovelreader.benchmark.StartupBenchmark#coldStartup"
```

结果、JUnit 报告和 Perfetto trace 会被归档到 `artifacts/benchmark/<时间戳>/`。

## 设备约束

- API 23 及以上；推荐 API 34 及以上。
- 正式对比数据优先使用温控稳定的实体设备。
- 模拟器适合验证流程和捕获 trace，但数值不应用作发布门禁。
- 运行期间保持设备空闲，关闭动画、自动更新和其他高负载进程。
