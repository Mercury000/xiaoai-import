import os

log_path = r'd:\mercu\Desktop\xiaoai\errors_utf8.txt'
if os.path.exists(log_path):
    with open(log_path, 'r', encoding='utf-8', errors='ignore') as f:
        lines = f.readlines()
        for line in lines:
            if 'e: ' in line:
                print(line.strip())
else:
    print("Log file not found.")
