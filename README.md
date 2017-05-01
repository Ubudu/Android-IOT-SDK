# Android-IoT-SDK

This SDK is dedicated to handling connection + bidirectional communication with the `ub_ble_scanner` device. SDK also provides an IBeacon advertising feature.

## Installing

Add Ubudu nexus repository url to your `build.gradle` file:

```
	repositories {
		mavenCentral()
		maven { url 'http://nexus.ubudu.com:8081/nexus/content/groups/public/' }
	}
```
    
Then add the following dependency:

```
    dependencies {
        compile('com.ubudu.iot:iot-sdk:1.0.0@aar')
        // …
    }
```

## How to use?

### u_ble_scanner discovery

Create an object implementing the `DongleFilter` interface. This interface is used to let the `DongleManager` class pick the desired device from all detectable devices nearby.:

	DongleFilter dongleFilter = new DongleFilter() {
        @Override
        public boolean isCorrect(BluetoothDevice device) {
        	// example implementation:
        	return device.getName() != null && device.getName().equals(MY_DONGLE_NAME);
        }
	};

Then to start Bluetooth LE discovery:
	
	dongleManager = new DongleManager(mContext);
	dongleManager.findDongle(dongleFilter, new DongleManager.DiscoveryListener() {
            @Override
            public void onDongleFound(Dongle dongle) {
                // matching dongle found
                mDongle = dongle;
            }

            @Override
            public void onDiscoveryError(Error error) {
                // Bluetooth LE discovery error
            }
    });
	

### u_ble_scanner connection

Before connecting to the detected dongle first set a connection interface:

	mDongle.setConnectionListener(new Dongle.ConnectionListener() {
            @Override
            public void onConnected() {
                // connected to dongle
            }

            @Override
            public void onDisconnected() {
                // disconnected from dongle
            }

            @Override
            public void onConnectionError(Error error) {
                // connection error
            }
    });

Then the following can be called:

	mDongle.connect(mContext);

When a dongle is already connected to another device the `onConnectionError(error)` is called after a while.

To disconnect from the dongle please call:

	mDongle.disconnect();

### u_ble_scanner communication

To communicate with the connected dongle first set a communication interface:

	mDongle.setCommunicationListener(new Dongle.CommunicationListener() {
            @Override
            public void onDataReceived(byte[] data) {
            	// data received from dongle
            }

            @Override
            public void onDataSent(byte[] data) {
            	// data sent to dongle
            }

            @Override
            public void onCommunicationError(Error error) {
            	// communication error
            }
        });

Then the following can be called:

	String message = "My message.";
	byte[] data = message.getBytes();
	mDongle.send(data);

### IBeacon advertising

Setup advertiser:

	Advertiser  advertiser = new Advertiser(mContext);
	advertiser.setListener(new Advertiser.EventListener() {
            @Override
            public void onAdvertisingStarted() {
            	// advertising started
            }

            @Override
            public void onAdvertisingStopped() {
            	// advertising started
            }

            @Override
            public void onAdvertisingError(Error error) {
            	// advertising error
            }
    });
    
Start advertising:

    advertiser.advertise("67D37FC1-BE36-4EF5-A24D-D0ECD8119A7D", "12", "312", -55);

Stop advertising:

	advertiser.stopAdvertising();