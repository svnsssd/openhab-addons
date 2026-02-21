# DahuaDoor Binding

This binding integrates Dahua VTO (Video Talk Outdoor) door stations with openHAB, enabling doorbell notifications, camera snapshots, and remote door control.

## Supported Things

| Thing Type | Thing ID   | Description                                      |
|------------|------------|--------------------------------------------------|
| Bridge     | `bridge`   | Dahua VTO Bridge - manages connection for all devices |
| VTO Device | `vto2202`  | Dahua VTO2202 - single doorbell button device    |
| VTO Device | `vto3211`  | Dahua VTO3211 - dual doorbell button device      |

## Discovery

Automatic discovery is not supported.
Things must be manually configured.

## Thing Configuration

### Bridge (`bridge`)

The bridge manages the DHIP connection to the Dahua device(s).
All VTO things must be configured as children of the bridge.

| Parameter    | Type | Required | Default | Description                                                   |
|--------------|------|----------|---------|---------------------------------------------------------------|
| hostname     | text | Yes      | -       | Hostname or IP address of the device (e.g., 192.168.1.100)   |
| username     | text | Yes      | -       | Username to access the device                                 |
| password     | text | Yes      | -       | Password to access the device                                 |
| snapshotpath | text | Yes      | -       | Linux path where image files are stored (e.g., /var/lib/openhab/door-images) |

**Note:** Windows paths are not currently supported.

### VTO2202 Device (`vto2202`)

Single doorbell button device (e.g., VTO2202F-P).

No additional configuration parameters required. All configuration is done on the bridge.

### VTO3211 Device (`vto3211`)

Dual doorbell button device (e.g., VTO3211D-P).

No additional configuration parameters required. All configuration is done on the bridge.

## Channels

### VTO2202 Channels (Single Button)

| Channel ID   | Type    | Read/Write | Description                                        |
|--------------|---------|------------|----------------------------------------------------|
| bell-button  | Trigger | Read       | Triggers when doorbell button is pressed (event: PRESSED) |
| door-image   | Image   | Read       | Camera snapshot taken when doorbell is pressed     |
| open-door-1  | Switch  | Write      | Command to open door relay 1                       |
| open-door-2  | Switch  | Write      | Command to open door relay 2                       |

### VTO3211 Channels (Dual Button)

| Channel ID     | Type    | Read/Write | Description                                        |
|----------------|---------|------------|----------------------------------------------------|
| bell-button-1  | Trigger | Read       | Triggers when doorbell button 1 is pressed (event: PRESSED) |
| bell-button-2  | Trigger | Read       | Triggers when doorbell button 2 is pressed (event: PRESSED) |
| door-image-1   | Image   | Read       | Camera snapshot for button 1                       |
| door-image-2   | Image   | Read       | Camera snapshot for button 2                       |
| open-door-1    | Switch  | Write      | Command to open door relay 1                       |
| open-door-2    | Switch  | Write      | Command to open door relay 2                       |

## Full Example

### Thing Configuration

#### VTO2202 (Single Button Device)

```java
Bridge dahuadoor:bridge:home "Dahua Bridge" @ "Network" [
    hostname="192.168.1.100",
    username="admin",
    password="password123",
    snapshotpath="/var/lib/openhab/door-images"
] {
    Thing vto2202 frontdoor "Front Door Station" @ "Entrance"
}
```

#### VTO3211 (Dual Button Device)

```java
Bridge dahuadoor:bridge:home "Dahua Bridge" @ "Network" [
    hostname="192.168.1.100",
    username="admin",
    password="password123",
    snapshotpath="/var/lib/openhab/door-images"
] {
    Thing vto3211 mainentrance "Main Entrance" @ "Building Entry"
}
```

### Item Configuration

#### For VTO2202 (Single Button)

```java
Switch OpenFrontDoor "Open Front Door" <door> { channel="dahuadoor:vto2202:home:frontdoor:open-door-1" }
Image FrontDoorImage "Front Door Camera Image" <camera> { channel="dahuadoor:vto2202:home:frontdoor:door-image" }
```

#### For VTO3211 (Dual Button)

```java
Switch OpenMainDoor "Open Main Door" <door> { channel="dahuadoor:vto3211:home:mainentrance:open-door-1" }
Image MainEntranceImage1 "Main Entrance Camera Image 1" <camera> { channel="dahuadoor:vto3211:home:mainentrance:door-image-1" }
Image MainEntranceImage2 "Main Entrance Camera Image 2" <camera> { channel="dahuadoor:vto3211:home:mainentrance:door-image-2" }
```

### Rule Configuration

#### VTO2202 - Single Button Notification

Send smartphone notification with camera image when doorbell is pressed (requires openHAB Cloud Connector):

```java
rule "Doorbell Notification"
when
    Channel "dahuadoor:vto2202:home:frontdoor:bell-button" triggered PRESSED
then
    sendBroadcastNotification("Visitor at the door", "door", 
        "entrance", "Entrance", "door-notifications", null, 
        "item:FrontDoorImage", 
        "Open Door=command:OpenFrontDoor:ON", null)
end
```

#### VTO3211 - Dual Button Notifications

```java
rule "Doorbell Button 1 Notification"
when
    Channel "dahuadoor:vto3211:home:mainentrance:bell-button-1" triggered PRESSED
then
    sendBroadcastNotification("Visitor at entrance 1", "door", 
        "entrance1", "Entrance 1", "door-notifications", null, 
        "item:MainEntranceImage1", 
        "Open Door=command:OpenMainDoor:ON", null)
end

rule "Doorbell Button 2 Notification"
when
    Channel "dahuadoor:vto3211:home:mainentrance:bell-button-2" triggered PRESSED
then
    sendBroadcastNotification("Visitor at entrance 2", "door", 
        "entrance2", "Entrance 2", "door-notifications", null, 
        "item:MainEntranceImage2", 
        "Open Door=command:OpenMainDoor:ON", null)
end
```
