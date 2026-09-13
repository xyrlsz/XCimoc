#!/usr/bin/env python3
"""Extract the latest version's changelog from CHANGELOG.md to release_body.md."""

import os

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.dirname(script_dir)
    changelog_path = os.path.join(repo_root, 'CHANGELOG.md')
    output_path = os.path.join(repo_root, 'release_body.md')

    with open(changelog_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 定位第一个 ## v 版本标题（跳过开头的提示块）
    parts = content.split('## v', 1)
    if len(parts) > 1:
        # 从第一个版本标题开始，到下一个 ## v 或文件末尾结束
        version_content = '## v' + parts[1].split('\n## v')[0]
        # 去掉尾部的 --- 分隔线和多余空白
        version_content = version_content.rstrip().rstrip('-').rstrip()
    else:
        # 保底：直接用完整文件
        version_content = content
    version_content = """
> [!TIP]
> `v1.10.9及以下的版本更新到v1.11.0以及以上的版本`
>
> 1. 由于更新了数据库框架，原有的数据需要迁移。
> 2. 请使用软件的备份恢复功能进行数据迁移。
> 3. 已下载漫画可以在设置里找到扫描选项
""" + "\n" + version_content

    with open(output_path, 'w', encoding='utf-8') as out:
        out.write(version_content.strip())

    print('Extracted release body:')
    print(version_content)


if __name__ == '__main__':
    main()
