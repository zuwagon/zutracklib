# zutracklib

zutracklib is Zuwagon's library to enable live tracking functionality in an application

## Installation

#### Step 1. Add the JitPack repository to your build file


```
Add it in your root build.gradle at the end of repositories:

	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```


#### Step 2. Add the dependency


```
	dependencies {
	        implementation 'com.github.zuwagon:zutracklib:v1.0.0'
	}
```

## How to use?

#### Step 1: Configure the library, with below method.


```
Zuwagon.configure(
    getApplicationContext(),
                "12345678",  //Unique rider Id
                "sample-auth-token", //Provided by Zuwagon
               0,
               "Zuwagon Channel title",
               "Rider",
               "Tracking on",
               "Notification ticker",
               0,
               0
    );
```


#### Step 2: Start tracking the rider


```
Zuwagon.startTrack(MainActivity.this); //pass context
```


#### Step 3: Stop tracking the rider


```
Zuwagon.stopTrack(MainActivity.this); //pass context
```



