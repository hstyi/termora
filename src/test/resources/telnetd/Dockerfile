FROM centos:7

RUN sed -i 's/mirrorlist/#mirrorlist/g' /etc/yum.repos.d/CentOS-*
RUN sed -i 's|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g' /etc/yum.repos.d/CentOS-*
RUN yum update -y
RUN yum install -y xinetd
RUN yum install -y telnet-server
RUN yum install -y epel-release
RUN yum install -y htop

RUN echo "pts/0" >> /etc/securetty
RUN echo "pts/1" >> /etc/securetty
RUN echo "pts/2" >> /etc/securetty


RUN echo "root:root" | chpasswd

RUN adduser user
RUN echo "user:user" | chpasswd

COPY <<EOF /etc/xinetd.d/telnet
service telnet
{
    flags = REUSE
    socket_type = stream
    wait = no
    user = root
    server = /usr/sbin/in.telnetd
    log_on_failure += USERID
    disable = no
}
EOF

COPY <<EOF /docker-entrypoint.sh
#!/bin/bash
xinetd -dontfork -stayalive
EOF

RUN chmod 777 docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]

# docker build -t telnetd .
# docker run -it --rm --name telnetd -p 2323:23 telnetd