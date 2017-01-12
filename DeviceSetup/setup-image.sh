# Script to setup new pi disc image with HappyBrackets and appropriate details

# run from pi, with sudo, with an internet connection

cd

# keep apt-get up to date with mirrors
sudo apt-get update

# install zeroconf
sudo apt-get install libnss-mdns
sudo apt-get install netatalk

# install i2c tools
sudo apt-get install i2c-tools

# install java 8
sudo apt-get install oracle-java8-jdk 

# Enable I2C on raspi, to connect to sensors. 
# Counter-intuitively 'do_i2c 0' means 'enable'. 
sudo raspi-config nonint do_i2c 0

# get 'interfaces' file and copy it to /etc/network
wget --no-check-certificate -N https://raw.githubusercontent.com/orsjb/HappyBrackets/master/DeviceSetup/interfaces
sudo mv interfaces /etc/network/interfaces

# get the happybrackets zipped project folder
wget --no-check-certificate -N http://www.happybrackets.net/downloads/HappyBracketsDeviceRuntime.zip
unzip HappyBracketsDeviceRuntime.zip
rm HappyBracketsDeviceRuntime.zip

# TODO setup audio if necessary.
# set audio output to max volume, well not quite max but close
amixer cset numid=1 0
# save audio settings
sudo alsactl store

# Setup autorun
wget --no-check-certificate -N https://raw.githubusercontent.com/orsjb/HappyBrackets/master/DeviceSetup/rc.local
sudo mv rc.local /etc/
chmod +x /etc/rc.local
