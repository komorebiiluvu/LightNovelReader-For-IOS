import Foundation
import UIKit

/// 崩溃报告捕获器。
///
/// 捕获两类崩溃并记录到沙盒文件（可在设置页导出）：
/// 1. 未捕获的 NSException（Objective-C/Swift 异常）
/// 2. 崩溃信号（SIGABRT/SIGSEGV/SIGBUS/SIGILL 等）
///
/// 注意：崩溃捕获只能"尽量"记录崩溃时的状态（写文件在信号 handler 里受限），
/// 无法阻止崩溃本身。捕获到崩溃后，下次启动会提示有上次崩溃报告可导出。
final class CrashReporter {
    static let shared = CrashReporter()

    private let crashDir: URL
    private var previousHandler: ((NSException) -> Void)?

    private init() {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        crashDir = base.appendingPathComponent("CrashReports", isDirectory: true)
        try? FileManager.default.createDirectory(at: crashDir, withIntermediateDirectories: true)
    }

    /// 安装崩溃捕获（app 启动时调用一次）
    func install() {
        previousHandler = NSGetUncaughtExceptionHandler()
        NSSetUncaughtExceptionHandler { exception in
            CrashReporter.shared.writeCrash(
                name: "NSException",
                reason: "\(exception.name.rawValue): \(exception.reason ?? "")",
                stack: exception.callStackSymbols.joined(separator: "\n")
            )
        }
        installSignalHandlers()
    }

    private func installSignalHandlers() {
        let signals: [Int32] = [SIGABRT, SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGTRAP]
        for sig in signals {
            signal(sig) { s in
                CrashReporter.shared.writeSignalCrash(signal: s)
            }
        }
    }

    private func writeSignalCrash(signal: Int32) {
        let name: String
        switch signal {
        case SIGABRT: name = "SIGABRT"
        case SIGSEGV: name = "SIGSEGV"
        case SIGBUS: name = "SIGBUS"
        case SIGILL: name = "SIGILL"
        case SIGFPE: name = "SIGFPE"
        case SIGTRAP: name = "SIGTRAP"
        default: name = "SIGNAL(\(signal))"
        }
        writeCrash(name: name, reason: "信号 \(name)", stack: Thread.callStackSymbols.joined(separator: "\n"))
    }

    private func writeCrash(name: String, reason: String, stack: String) {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd_HHmmss"
        let stamp = formatter.string(from: Date())
        let file = crashDir.appendingPathComponent("crash_\(stamp).txt")

        let device = UIDevice.current
        let header = """
        LightNovelReader 崩溃报告
        时间: \(Date())
        设备: \(device.model) \(device.systemName) \(device.systemVersion)
        版本: \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?")
        ========================================
        类型: \(name)
        原因: \(reason)
        ========================================
        堆栈:
        \(stack)

        """

        try? header.write(to: file, atomically: true, encoding: String.Encoding.utf8)
    }

    /// 已记录的崩溃报告列表（按时间倒序）
    var crashFiles: [URL] {
        let files = (try? FileManager.default.contentsOfDirectory(at: crashDir, includingPropertiesForKeys: [.creationDateKey])) ?? []
        return files.sorted { ($0.creationDate ?? .distantPast) > ($1.creationDate ?? .distantPast) }
    }

    /// 是否有待导出的崩溃报告
    var hasCrashes: Bool { !crashFiles.isEmpty }

    /// 崩溃报告内容（拼接所有，用于导出）
    func exportContent() -> String {
        crashFiles.compactMap { try? String(contentsOf: $0, encoding: .utf8) }.joined(separator: "\n\n")
    }

    /// 清空崩溃报告
    func clear() {
        for file in crashFiles {
            try? FileManager.default.removeItem(at: file)
        }
    }

    /// 写入一条测试崩溃记录（验证捕获/导出链路用）
    func writeTestCrash() {
        writeCrash(name: "Test", reason: "测试崩溃", stack: "test stack\nline2")
    }
}

private extension URL {
    var creationDate: Date? {
        (try? resourceValues(forKeys: [.creationDateKey]))?.creationDate
    }
}
