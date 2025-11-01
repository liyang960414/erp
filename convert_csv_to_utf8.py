#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
转换CSV文件编码为UTF-8
"""

import sys
import chardet

def convert_to_utf8(input_file, output_file):
    # 尝试检测文件编码
    with open(input_file, 'rb') as f:
        raw_data = f.read()
        detected = chardet.detect(raw_data)
        encoding = detected['encoding']
        print(f"检测到编码: {encoding} (置信度: {detected['confidence']:.2%})")
    
    # 尝试多种常见中文编码
    encodings_to_try = [encoding] if encoding else ['gbk', 'gb2312', 'gb18030', 'utf-8', 'latin1']
    
    for enc in encodings_to_try:
        try:
            print(f"尝试使用编码: {enc}")
            with open(input_file, 'r', encoding=enc, errors='ignore') as f:
                content = f.read()
            
            # 写入UTF-8格式
            with open(output_file, 'w', encoding='utf-8') as f:
                f.write(content)
            
            print(f"成功转换！输出文件: {output_file}")
            
            # 显示前几行以便验证
            print("\n转换后的前3行:")
            lines = content.split('\n')[:3]
            for i, line in enumerate(lines, 1):
                print(f"{i}: {line[:100]}...")
            
            return True
        except Exception as e:
            print(f"使用 {enc} 编码失败: {e}")
            continue
    
    print("所有编码尝试都失败了！")
    return False

if __name__ == '__main__':
    input_file = r'example\物料.csv'
    output_file = r'example\物料_utf8.csv'
    
    convert_to_utf8(input_file, output_file)

