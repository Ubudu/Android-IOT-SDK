# Android-IoT-SDK

This SDK is dedicated for handling discovery, connection and two way communication with the `ub_ble_scanner` dongle. 
SDK also provides API to make mobile device act like a connectable `ub_ble_scanner` device.

## Installing

Add Ubudu nexus repository url to your `build.gradle` file:

	repositories {
		maven { url 'http://nexus.ubudu.com:8081/nexus/content/groups/public/' }
		// ...
	}
    
Then add the following dependency:

    dependencies {
        compile('com.ubudu.iot:iot-sdk:1.3.1@aar')
        // ...
    }

## How to use?

### u\_ble\_scanner discovery

Create an object implementing the `DongleFilter` interface. This interface is used to let the `DongleManager` class pick the desired device from all detectable devices nearby.:

	BleDeviceFilter dongleFilter = new BleDeviceFilter() {
        @Override
        public boolean isCorrect(BluetoothDevice device, int rssi, byte[] scanResponse) {
        	// example implementation:
        	return device.getName() != null && device.getName().equals(MY_DONGLE_NAME);
        }
	};

**NOTE:** At this point on Android N `scanResponse` is always `null`.

Then to discover a dongle:
	
	DongleManager.findDongle(getApplicationContext(), 5000, dongleFilter, new DongleManager.DiscoveryListener() {

		@Override
		public boolean onDongleFound(Dongle dongle) {
		//	matching dongle found
			mDongle = dongle;
			return true;
		}
	
		@Override
		public void onDiscoveryError(Error error) {
			// Bluetooth LE discovery error
		}

       void onDiscoveryStarted() {
       	// BLE discovery started
       }

       void onDiscoveryFinished() {
       	// BLE discovery finished
       }
	});
	
The flow of discovery is as follows:

- discovery lasts for the amount of milliseconds specified in the argument of `DongleManager.findDongle` method,
 
- during this time all detected BLE devices matching the given `BleDeviceFilter` implementation are returned in the `onDongleFound` method,
 
- if at some point the `onDongleFound` returns `true`, the discovery will be stopped immediately. 


### u\_ble\_scanner connection

Then to connect to the dongle call the following:

	mDongle.connect(mContextnew BleDevice.ConnectionListener() {
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

When a dongle is already connected to another device the `onConnectionError(error)` is called after a while.

To disconnect from the dongle please call:

	mDongle.disconnect();

### u\_ble\_scanner communication

Before communicating with the dongle a data received interface should be set:

	mDongle.setDataReceivedEventListener(new BleDevice.ReceiveDataEventListener() {
		@Override
		public void onDataReceived(byte[] data) {
			// data received from dongle
		}
	});

Then the `send(byte[] data)` can be called:

	String message = "My message.";
	byte[] data = message.getBytes();
	mDongle.send(data, new BleDevice.SendDataEventListener() {
		@Override
		public void onDataSent(byte[] data) {
			// data sent
		}
		
		@Override
		public void onCommunicationError(Error error) {
			// communication error
		}
	});

### Make mobile device act as a connectable dongle

To make that happen the following code should be called to setup the `BluetoothGattServer`:

	peripheralManager = new PeripheralManager(getApplicationContext(), new DeviceProfile() {
	
		@Override
		public String getServiceUuid() {
			// service UUID to expose characteristics
			return "D9500001-F608-42CD-A37E-92A559491B2B";
		}
		
		@Override
		public String getWriteCharacteristicUuid() {
			// write-only characteristic UUID
			return "D9500002-F608-42CD-A37E-92A559491B2B";
		}
		
		@Override
		public String getReadCharacteristicUuid() {
			// read-only characteristic UUID
			return "D9500003-F608-42CD-A37E-92A559491B2B";
		}
	});
	
	peripheralManager.setEventListener(new PeripheralManager.PeripheralListener() {
	
		@Override
		public void onPeripheralReady() {
			// gatt server is ready to handle connections and communication
		}
		
		@Override
		public void onConnectionStateChange(BluetoothDevice device, String stateDescription) {
			// outside device connection state changed
		}
		
		@Override
		public void onCharacteristicWritten(UUID characteristicUUID, String value) {
			// on data received
		}
		
		@Override
		public void onCharacteristicRead(UUID characteristicUUID, String value) {
			// on data read
		}
		
		@Override
		public void onPeripheralError(Error error) {
			// error event
		}
	});
	
	peripheralManager.openGattServer();

To stop the device from being a connectable peripheral:

	peripheralManager.closeGattServer();

After `BluetoothGattServer` is active the mobile device must start advertising some Bluetooth LE packets to start being discoverable by other devices nearby.
Setting up the advertiser is as follows:

	Advertiser  advertiser = new Advertiser(mContext);
	advertiser.setListener(new Advertiser.AdvertisingListener() {
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
    
Start advertising as IBeacon:

    advertiser.advertise(IBeacon.getAdvertiseBytes("67D37FC1-BE36-4EF5-A24D-D0ECD8119A7D", "12", "312", -55),true);

- the end `boolean` argument determines whether the mobile device is connectable or not. `true` must be set to allow devices nearby to connect,
- in the example above the advertisement is an IBeacon advertisement but the `advertise` method takes any byte array data (this SDK already provides the static method `IBeacon.getAdvertiseBytes`).

To stop advertising at any time call the following:

	advertiser.stopAdvertising();