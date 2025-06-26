# 使用官方 Python 轻量级基础镜像
FROM python:3.11-slim

# 设置工作目录
WORKDIR /app

# 将当前目录下的 server.py 复制到容器的 /app 目录下
COPY server.py .

RUN pip install -i https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple flask gunicorn

# 暴露5050端口
EXPOSE 5050

# 运行 server.py
#CMD ["python", "server.py"]
CMD ["gunicorn", "--bind", "0.0.0.0:5050", "server:app"]
