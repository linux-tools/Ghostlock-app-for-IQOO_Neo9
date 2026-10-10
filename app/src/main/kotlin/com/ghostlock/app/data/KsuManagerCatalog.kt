package com.ghostlock.app.data

import android.content.Context
import java.io.File

/**
 * 第三方 KernelSU 系管理器目录。
 *
 * 激活 KernelSU 的本质是把这两件事换成选中管理器的：
 *   1. ksud 来源 —— `find /data/app -name libksud.so | grep <pkg> | head -1`
 *   2. `late-load --package-name <pkg> --kmi <kmi>`（ksud 从该包读取 `<kmi>_kernelsu.ko`）
 *
 * 因此选择结果通过 filesDir 下的 `.gl_ksu_pkg` / `.gl_kmi` 交给 root 脚本，
 * 脚本运行时读取，无需重新编译 native。未安装/包名未知的管理器用自定义输入。
 */
object KsuManagerCatalog {
    data class Manager(val label: String, val packageName: String)

    /** 预设顺序即 root 脚本的兜底查找顺序（pkgs 为空表示必须自定义包名）。 */
    val managers = listOf(
        Manager("KernelSU（官方）", "me.weishu.kernelsu"),
        Manager("KernelSU-Next", "com.rifsxd.ksunext"),
        Manager("SukiSU Ultra", "com.sukisu.ultra"),
        Manager("ReSukiSU", "com.resukisu.resukisu"),
        Manager("KowSU / SuperManager", "com.kowx712.supermanager"),
        // 7kimisu：applicationId = com.sevenk.core（PR 构建为 com.sevenk.core.pr）
        Manager("7kimisu", "com.sevenk.core"),
        Manager("自定义包名", ""),
    )

    const val DEFAULT_PACKAGE = "me.weishu.kernelsu"
    const val DEFAULT_KMI = "android13-5.15"

    /** ksud (libksud.so) 的兜底查找顺序；管理器 APK 不再随包分发。 */
    val managerPackages: List<String> = listOf("me.weishu.kernelsu.pr") + managers.mapNotNull {
        it.packageName.takeIf(String::isNotBlank)
    }

    private const val PREFS = "ghostlock_ksu_selection"
    private const val KEY_PKG = "package"
    private const val KEY_LABEL = "label"
    private const val KEY_KMI = "kmi"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selectedPackage(context: Context): String =
        prefs(context).getString(KEY_PKG, DEFAULT_PACKAGE).orEmpty().ifBlank { DEFAULT_PACKAGE }

    fun selectedLabel(context: Context): String =
        prefs(context).getString(KEY_LABEL, managers.first().label).orEmpty()

    fun kmi(context: Context): String =
        prefs(context).getString(KEY_KMI, DEFAULT_KMI).orEmpty().ifBlank { DEFAULT_KMI }

    fun save(context: Context, manager: Manager, customPackage: String) {
        val pkg = manager.packageName.ifBlank { customPackage.trim() }
        prefs(context).edit()
            .putString(KEY_LABEL, manager.label)
            .putString(KEY_PKG, pkg.ifBlank { DEFAULT_PACKAGE })
            .apply()
    }

    fun saveKmi(context: Context, kmi: String) {
        prefs(context).edit()
            .putString(KEY_KMI, kmi.trim().ifBlank { DEFAULT_KMI })
            .apply()
    }

    fun selectedIndex(context: Context): Int {
        val label = selectedLabel(context)
        val index = managers.indexOfFirst { it.label == label }
        return if (index >= 0) index else managers.lastIndex
    }

    /** 选中项是"自定义/未收录"，需要用户填包名。 */
    fun needsCustomPackage(context: Context): Boolean =
        managers[selectedIndex(context)].packageName.isBlank()

    /** 把选择写入 root 脚本读取的文件（每次运行前调用）。 */
    fun writeRuntimeConfig(context: Context) {
        runCatching {
            File(context.filesDir, ".gl_ksu_pkg").writeText(selectedPackage(context) + "\n")
            File(context.filesDir, ".gl_kmi").writeText(kmi(context) + "\n")
        }
    }
}
