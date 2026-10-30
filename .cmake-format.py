# .cmake-format.py

with section("format"):
    # 最大行宽，与 .clang-format 的 ColumnLimit 保持一致。
    line_width = 120

    # 每级缩进 4 个空格。
    tab_size = 4

    # 不使用 Tab。
    use_tabchars = False

    # 多行命令的右括号单独一行。
    dangle_parens = True

    # 右括号与命令名对齐。
    dangle_align = "prefix"

    # 现代 CMake 命令统一使用小写：
    # add_library(...)
    # target_link_libraries(...)
    command_case = "lower"

    # CMake 参数关键字统一使用大写：
    # PRIVATE / PUBLIC / REQUIRED / CONFIG
    keyword_case = "upper"

    # 输出统一使用 LF。
    line_ending = "unix"

    # 不在函数名和括号之间增加空格。
    separate_fn_name_with_space = False
    separate_ctrl_name_with_space = False

    # 不自动猜测哪些参数应该排序，避免 formatter 意外改变语义/人为顺序。
    autosort = False
