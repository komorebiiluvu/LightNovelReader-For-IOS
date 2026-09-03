import Foundation
import UIKit

/// 崩溃报告捕获器。
///
/// 捕获未处理的 NSException 并记录到沙盒文件（可在设置页导出）。
///
/// 不拦截 SIGSEGV/SIGABRT 等 POSIX 信号：信号处理器中调用 Foundation、分配内存或
/// 写普通文件都不是 async-signal-safe，容易二次崩溃或反复触发同一故障指令。
/// 若后续需要完整的 Swift/native 崩溃堆栈，应接入 PLCrashReporter/KSCrash 或 MetricKit。
final class CrashReporter {
    static let shared = CrashReporter()

    private let crashDir: URL
    private var previousHandler: NSUncaughtExceptionHandler?
    private var installed = false
    private let maxCrashFiles = 20

    private init() {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        crashDir = base.appendingPathComponent("CrashReports", isDirectory: true)
        try? FileManager.default.createDirectory(at: crashDir, withIntermediateDirectories: true)
    }

    /// 安装崩溃捕获（app 启动时调用一次）
    func install() {
        guard !installed else { return }
        installed = true
        previousHandler = NSGetUncaughtExceptionHandler()
        NSSetUncaughtExceptionHandler { exception in
            CrashReporter.shared.writeCrash(
                name: "NSException",
                reason: "\(exception.name.rawValue): \(exception.reason ?? "")",
                stack: exception.callStackSymbols.joined(separator: "\n")
            )
            CrashReporter.shared.previousHandler?(exception)
        }
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
        pruneCrashFilesIfNeeded()
    }

    private func pruneCrashFilesIfNeeded() {
        for file in crashFiles.dropFirst(maxCrashFiles) {
            try? FileManager.default.removeItem(at: file)
        }
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
