"""Create a local-only APK signing identity; never prints passwords."""
from pathlib import Path
import os, secrets, subprocess
root=Path(__file__).resolve().parents[1]
props=root/'keystore.properties'
if props.exists():
    raise SystemExit('Signing configuration already exists; keeping it unchanged.')
private=root/'private';private.mkdir(exist_ok=True,mode=0o700)
key=private/'depthwall-release.jks'
if key.exists():
    raise SystemExit('Keystore already exists; restore its configuration instead of replacing it.')
password=secrets.token_urlsafe(36)
env=dict(os.environ,DEPTHWALL_KEY_PASSWORD=password)
subprocess.run(['keytool','-genkeypair','-keystore',str(key),'-storetype','JKS','-storepass:env','DEPTHWALL_KEY_PASSWORD','-keypass:env','DEPTHWALL_KEY_PASSWORD','-alias','depthwall','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=DepthWall Personal, OU=Personal Android Apps'],check=True,env=env,stdout=subprocess.DEVNULL)
props.write_text(f'storeFile=private/depthwall-release.jks\nstorePassword={password}\nkeyAlias=depthwall\nkeyPassword={password}\n')
os.chmod(props,0o600);os.chmod(key,0o600)
print('Personal signing identity created. Back up private/ and keystore.properties securely; both are gitignored.')
