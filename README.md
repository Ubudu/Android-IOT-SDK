# Android-IoT-SDK

This SDK is dedicated for handling discovery, connection and two way communication with a Bluetooth LE device. 
SDK also provides API to make mobile device act like a connectable Bluetooth LE device.

## Installing

Add Ubudu nexus repository url to your `build.gradle` file:

	repositories {
		maven { url 'http://nexus.ubudu.com:8081/nexus/content/groups/public/' }
		// ...
	}
    
Then add the following dependency:

    implementation 'com.ubudu.iot:iot-sdk:1.6.5@aar'

## How to use?

### Bluetooth LE discovery

Create an object implementing the `BleDeviceFilter` interface. This interface is used to let
the `DiscoveryManager` class pick the desired device from all devices being detected nearby.:

	BleDeviceFilter bleDeviceFilter = new BleDeviceFilter() {
        @Override
        public boolean isCorrect(BluetoothDevice device, int rssi, byte[] scanResponse) {
        	// example implementation:
        	return device.getName() != null && device.getName().equals("MyDongle");
        }
	};

Then to discover a Bluetooth LE device:
	
	DiscoveryManager.discover(mContext, 5000, bleDeviceFilter, new DiscoveryManager.DiscoveryListener() {

		@Override
		public boolean onBleDeviceFound(BleDevice bleDevice) {
		    // ble device that matches the given bfound
			mBleDevice = bleDevice;
			// Returning true will stop the BLE device scanner immediately.
            // Returning false will make scanning last according to
            // given duration or until stop() is called.
			return true;
		}
	
		@Override
		public void onDiscoveryError(Error error) {
			// Bluetooth LE discovery error
		}
		
		@Override
       public void onDiscoveryStarted() {
       	// BLE discovery started
       }
       
       @Override
       public void onDiscoveryFinished() {
       	// BLE discovery finished
       }
	});
	
The flow of discovery is as follows:

- discovery lasts for the amount of milliseconds specified in the argument of `DiscoveryManager.discover` method,
 
- during this time all detected Bluetooth LE devices matching the given `BleDeviceFilter` implementation are returned in the `onBleDeviceFound` callback,
 
- if at some point the `onBleDeviceFound` implementation returns `true`, the discovery will be stopped immediately. 


### Bluetooth LE connection

It is possible to negotiate MTU of the connection. To enable negotiation the following must be called bofore connecting:

    this.mBleDevice.setNegotiateMtuEnabled(true);

By default the SDK will try to negotiate maximum MTU of 276 bytes. To ask for specific MTU size the following method must be called:

    this.mBleDevice.setDesiredMtu(mDesiredMtuInt);

Note that some Android devices do not work well if you try to force MTU and communication can break of freeze because of that. 
It depends on the particular device.

Then to connect to the BLE device call the following:

	mBleDevice.connect(mContext, new BleDevice.ConnectionListener() {
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

To disconnect from the device please call:

	mBleDevice.disconnect();

### Bluetooth LE communication

Before communicating with the device its Bluetooth GATT services have to be discovered:

    mBleDevice.discoverServices(new BleDevice.ServicesDiscoveryListener() {
        @Override
        public void onServicesDiscovered(List<BluetoothGattService> services) {
            // services found
        }
        
        @Override
        public void onError(Error error) {
            // error
        }
    });

With the Bluetooth LE services of the device discovered it is possible to establish a 2 way 
communication channel:

1) In order to be able to receive data from Bluetooth LE device one of the GATT characteristics 
available within one of the services should have `BluetoothGattCharacteristic.PROPERTY_NOTIFY` property.
2) In order to be able to send data to Bluetooth LE device one of the GATT characteristics 
available within one of the services should have `BluetoothGattCharacteristic.PROPERTY_WRITE` or 
`BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE` property.

It is possible to use single GATT characteristic for both purposes if device provides such characteristic.
    
    mBleDevice.registerForNotifications(characteristic, new BleDevice.RegisterForNotificationsListener() {
        @Override
        public void onRegistered(BluetoothGattCharacteristic gattCharacteristic) {
            // success
        }
        
        @Override
        public void onError(Error error) {
            // error
        }
    });
            
Data being received from the Bleutooth LE device is received by the listener interface that has to be set:

    mBleDevice.setDataReceivedEventListener(new DataListener() {
        @Override
        public void onDataReceived(byte[] data) {
            // data received from the device
    	}
    });

The `DataListener` can be set any time before registering for notifications, e.g. right after successful connection.


To send data to the Bleutooth LE device:

	String message = "My message.";
	mBleDevice.send(message.getBytes(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT, gattCharacteristic);

To be notified about the result of sending the data a `DataSentListener` implementation must be set on the `BleDevice` instance:

	mBleDevice.setDataSentListener(new DataSentListener() {
	    @Override
	    public void onDataSent(byte[] data) {
	        // data sent successfuly
	    }
	    
	    @Override
	    public void onError(Error error) {
	        // error
	    }
	});

### Make mobile device act as a connectable Bluetooth LE device

To make that happen the Bluetooth GATT server has to be opened. The following code configures it:

	peripheralManager = new PeripheralManager(mContext, new DeviceProfile() {
	
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
	
To open the Bluetooth GATT server:

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
	
Foreign Bluetooth LE device can register for notifications to the read characteristic specified in `getReadCharacteristicUuid` method being a `DeviceProfile` implementation. If mobile device acting as peripheral wants to write some data to the foreign device then this read characteristic must be written. It is done when the following method is used:

	peripheralManager.writeData(data);
	
The data received from the foreign device will trigger a `onCharacteristicWritten` callback of the given `PeripheralManager.PeripheralListener` instance.
