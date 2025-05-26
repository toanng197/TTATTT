import math
p=int(input())
q=int(input())
e=int(input())
def gcd(a,b):
    while b!=0:
        r=a%b
        a=b
        b=r
    return a
def nghichdao(a,b):
    u=a
    v=b
    x1=1
    x2=0
    while u!=1:
        q=v//u
        r=v-q*u
        x=x2-q*x1
        v=u
        u=r
        x2=x1
        x1=x
    if x1<0:
        return x1+b
    return x1
def rsa(p,q,e):
    phin=(p-1)*(q-1)
    return nghichdao(e,phin)
print(p*q)
print(rsa(p,q,e))