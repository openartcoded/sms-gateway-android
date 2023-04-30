# Sms Gateway

Android app that polls sms request from a backend server and send them.

- Requires a mqtt broker, connect using the credentials set in the mobile app
- Subscribes to a queue named "sms" 
- message to be published on that queue should look like this:
    ```json
      {
        "phoneNumber": "+32488111111",
        "message": "Hello world!"
      }
    ```