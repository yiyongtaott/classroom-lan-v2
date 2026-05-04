import os

# --- 配置区 ---
# 自动识别的目标目录名（支持模糊匹配或直接指定）
FRONTEND_DIR_NAME = "frontend"
BACKEND_DIR_NAME = "backend"

# 排除的目录名
EXCLUDE_DIRS = {
    'target', 'node_modules', 'resources', '.git', '.idea', 
    'dist', '.vscode', 'bin', 'obj'
}

# 允许读取的文件后缀
ALLOWED_EXTENSIONS = {
    '.java', '.xml', '.yml', '.properties',  # 后端常见
    '.vue', '.js', '.ts', '.jsx', '.tsx', '.json', '.html' # 前端常见
}

def is_excluded(path):
    """判断路径是否在排除名单中"""
    parts = path.split(os.sep)
    return any(exclude in parts for exclude in EXCLUDE_DIRS)

def collect_files(root_path):
    """智能识别并遍历文件"""
    print(f"正在扫描目录: {os.path.abspath(root_path)}")
    
    # 结果输出到当前目录的 project_summary.txt
    with open("project_summary.txt", "w", encoding="utf-8") as f_out:
        for root, dirs, files in os.walk(root_path):
            # 过滤不需要的文件夹（原地修改 dirs 影响 os.walk 的后续遍历）
            dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
            
            if is_excluded(root):
                continue
                
            for file in files:
                ext = os.path.splitext(file)[1].lower()
                if ext in ALLOWED_EXTENSIONS:
                    file_path = os.path.join(root, file)
                    relative_path = os.path.relpath(file_path, root_path)
                    
                    try:
                        with open(file_path, "r", encoding="utf-8") as f_in:
                            content = f_in.read()
                            
                        # 写入文件标记位，方便 AI 阅读
                        f_out.write(f"\n\n{'='*80}\n")
                        f_out.write(f"FILE: {relative_path}\n")
                        f_out.write(f"{'='*80}\n\n")
                        f_out.write(content)
                        print(f"已处理: {relative_path}")
                    except Exception as e:
                        print(f"无法读取 {relative_path}: {e}")

if __name__ == "__main__":
    current_dir = os.getcwd()
    collect_files(current_dir)
    print("\n完成！所有代码已合并至 project_summary.txt")