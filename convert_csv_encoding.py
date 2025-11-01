#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""将CSV文件从GBK编码转换为UTF-8编码"""

import sys

def convert_csv_encoding(input_file, output_file, source_encoding='gbk', target_encoding='utf-8'):
    """转换CSV文件编码"""
    try:
        # 尝试读取原始文件
        with open(input_file, 'r', encoding=source_encoding, errors='ignore') as f:
            content = f.read()
        
        # 写入UTF-8格式
        with open(output_file, 'w', encoding=target_encoding, newline='') as f:
            f.write(content)
        
        print(f"成功将 {input_file} 从 {source_encoding} 转换为 {target_encoding}")
        print(f"输出文件: {output_file}")
        
        # 打印前几行供检查
        lines = content.split('\n')
        print("\n前5行内容:")
        for i, line in enumerate(lines[:5], 1):
            print(f"{i}: {line[:200]}")  # 每行最多显示200个字符
        
        return True
    except Exception as e:
        print(f"转换失败: {e}")
        return False

if __name__ == '__main__':
    input_file = 'example/物料.csv'
    output_file = 'example/物料_utf8.csv'
    
    # 尝试不同的源编码
    encodings = ['gbk', 'gb2312', 'gb18030', 'utf-8']
    
    for encoding in encodings:
        print(f"\n尝试使用 {encoding} 编码...")
        if convert_csv_encoding(input_file, output_file, source_encoding=encoding):
            break
    else:
        print("所有编码尝试都失败了")
