import requests
import os

# 创建cn.conf文件
cn_conf_path = "D:\\cn.conf"

# 清空现有文件（如果存在）
if os.path.exists(cn_conf_path):
    with open(cn_conf_path, 'w', encoding='utf-8') as f:
        f.write('')

# 下载文件的URL列表
urls = [
    "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/direct-list.txt",
    "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/apple-cn.txt",
    "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/google-cn.txt"
]

# 下载并合并文件
for url in urls:
    try:
        print(f"正在下载 {url.split('/')[-1]}...")
        response = requests.get(url, timeout=30)
        response.raise_for_status()
        
        # 将内容追加到cn.conf
        with open(cn_conf_path, 'a', encoding='utf-8') as f:
            f.write(response.text)
        
        print(f"{url.split('/')[-1]} 下载完成")
    except Exception as e:
        print(f"下载 {url.split('/')[-1]} 失败: {str(e)}")

# 处理文件内容
print("正在处理文件内容...")
processed_lines = []

with open(cn_conf_path, 'r', encoding='utf-8') as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        
        # 跳过regexp开头的行
        if line.startswith('regexp:'):
            continue
        
        # 去除full:前缀
        if line.startswith('full:'):
            line = line[5:]
        
        if not line:
            continue
        
        # 添加nameserver格式
        processed_line = f"nameserver /{line}/cn"
        processed_lines.append(processed_line)

# 写回处理后的内容
with open(cn_conf_path, 'w', encoding='utf-8') as f:
    for line in processed_lines:
        f.write(line + '\n')

print("文件处理完成！")
print(f"cn.conf文件已创建在: {cn_conf_path}")
print(f"文件包含 {len(processed_lines)} 行内容")
