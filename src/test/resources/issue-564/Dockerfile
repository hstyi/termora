FROM debian:bookworm-slim
ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=Asia/Shanghai
# 安装基础包 + sshd + nvim 依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    openssh-server curl ca-certificates tzdata git unzip \
    libfuse2 locales && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
# 安装 nvim 最新版（AppImage 提取）
RUN curl -LO https://github.com/neovim/neovim/releases/download/v0.11.1/nvim-linux-arm64.appimage && \
    mv nvim-linux-arm64.appimage nvim.appimage && chmod u+x nvim.appimage && ./nvim.appimage --appimage-extract && \
    mv squashfs-root/usr/bin/nvim /usr/local/bin/nvim && \
    rm -rf squashfs-root nvim.appimage
# 配置 SSH
RUN mkdir /var/run/sshd && \
    echo 'root:root' | chpasswd && \
    echo 'PermitRootLogin yes' >> /etc/ssh/sshd_config && \
    echo 'PasswordAuthentication yes' >> /etc/ssh/sshd_config
# 设置语言环境（可选）
RUN echo "en_US.UTF-8 UTF-8" > /etc/locale.gen && \
    apt-get update && apt-get install -y locales && \
    locale-gen en_US.UTF-8 && \
    update-locale LANG=en_US.UTF-8
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8
# 启动 SSHD
EXPOSE 22
CMD ["/usr/sbin/sshd", "-D"]