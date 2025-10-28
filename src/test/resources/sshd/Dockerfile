FROM linuxserver/openssh-server:9.3_p2-r1-ls147
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.tuna.tsinghua.edu.cn/g' /etc/apk/repositories \
    && apk update && apk add wget tmux gcc zip p7zip g++ git make zsh htop stress-ng inetutils-telnet xclock xcalc xorg-server xinit && wget https://ohse.de/uwe/releases/lrzsz-0.12.20.tar.gz \
    && tar -xf lrzsz-0.12.20.tar.gz && cd lrzsz-0.12.20 && ./configure && make && make install \
    && ln -s /usr/local/bin/lrz /usr/local/bin/rz && ln -s /usr/local/bin/lsz /usr/local/bin/sz
RUN sed -i 's/#AllowAgentForwarding yes/AllowAgentForwarding yes/g' /etc/ssh/sshd_config
RUN sed -i 's/AllowTcpForwarding no/AllowTcpForwarding yes/g' /etc/ssh/sshd_config
RUN sed -i 's/GatewayPorts no/GatewayPorts yes/g' /etc/ssh/sshd_config
RUN sed -i 's/X11Forwarding no/X11Forwarding yes/g' /etc/ssh/sshd_config

# docker build -t sshd .
# docker run --rm -it -e SUDO_ACCESS=true -e PASSWORD_ACCESS=true -e USER_PASSWORD=123456 -p 2222:2222 sshd