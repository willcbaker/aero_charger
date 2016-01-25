package com.wakebyte.aerocharger;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.openxc.VehicleManager;
import com.openxc.messages.CanMessage;
import com.openxc.messages.VehicleMessage;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

    private static final String TAG = "ChargerActivity";
    private static final int MAX_VOLTAGE = 115;
    private static final int MIN_VOLTAGE = 3;
    private static final int DEFAULT_VOLTAGE = 100;
    private static final int DEFAULT_VOLTAGE_TENTHS = 8;
    private static final int MAX_CURRENT = 25;
    private static final int MIN_CURRENT = 0;
    private static final int DEFAULT_CURRENT = 25;
    private static final int DEFAULT_POWER = 50;

    private static int C_BYTE_VOLT_MSB = 6;
    private static int C_BYTE_VOLT_LSB = 5;
    private static int C_BYTE_AMP_MSB = 4;
    private static int C_BYTE_AMP_LSB = 3;
    private static int C_BYTE_PWR_MSB = 2;
    private static int C_BYTE_PWR_LSB = 1;
    private static int C_BYTE_ENABLE = 0;

    private static final int CHARGER_ID = 0x2FF;

    private static final int BUS_NUMBER = 1;
    public static final int CHARGE_DELAY = 500;

    private ToggleButton button_charge;
    private Button button_apply;
    private Switch switch_enable;
    private Switch switch_lock;
    private TextView text_power_percent;
    private NumberPicker number_voltage;
    private NumberPicker number_amps;
    private NumberPicker number_voltage_tenths;
    private NumberPicker number_amps_tenths;
    private ProgressBar progress_charge;
    private SeekBar slider_power;

    VehicleManager mVehicleManager;

    private ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the VehicleManager service is established
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i("openxc", "Bound to VehicleManager");
            // When the VehicleManager starts up, we store a reference to it
            // here in "mVehicleManager" so we can call functions on it
            // elsewhere in our code.
            mVehicleManager = ((VehicleManager.VehicleBinder) service)
                    .getService();

            // We want to receive updates whenever the EngineSpeed changes. We
            // have an EngineSpeed.Listener (see above, mSpeedListener) and here
            // we request that the VehicleManager call its receive() method
            // whenever the EngineSpeed changes
            //mVehicleManager.addListener(EngineSpeed.class, mSpeedListener);
            mVehicleManager.addListener(CanMessage.class, mListener);
        }

        // Called when the connection with the service disconnects unexpectedly
        public void onServiceDisconnected(ComponentName className) {
            Log.w("openxc", "VehicleManager Service  disconnected unexpectedly");
            mVehicleManager = null;
        }
    };
    private byte[] charge_byte = new byte[8];/*{
            (byte)0x00,(byte)0x00,
            (byte)0x00,(byte)0x00,
            (byte)0x00,(byte)0x00,
            (byte)0x00,(byte)0x00
    };*/

    private byte msb(int val){
        return (byte) ((val & 0xFF00)>>8);
    }
    private byte lsb(int val){
        return (byte) (val & 0xFF);
    }
    private byte[] convert(int ones, int tenths){
        //if (tenths > 9 || tenths < 0)
        int val = ones*10;
        val+= tenths;
        byte[] data = {msb(val),lsb(val)};//msb
        return data;

    }
    static final int MSB = 0;
    static final int LSB = 1;
    private byte[] charge(int voltage, int voltage_tenths,
                          int current, int current_tenths,
                          int power, boolean enable){
        byte[] b_voltage = convert(voltage,voltage_tenths);
        byte[] b_current = convert(current,current_tenths);
        byte[] b_power   = convert(power,0);
        byte b_enable = enable?((byte)0x01):((byte)0x00);
        byte[] data= {b_enable,
                b_power[LSB],b_power[MSB],
                b_current[LSB],b_current[MSB],
                b_voltage[LSB],b_voltage[MSB],
                (byte) 0x00,
        };
        return data;
    }
    public static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for(byte b : in) {
            builder.append(String.format("%02x ", b));
        }
        return builder.toString();
    }
    private void send_charge(){
        if (mVehicleManager != null) {
            CanMessage msg = new CanMessage(BUS_NUMBER, CHARGER_ID, charge_byte);
            mVehicleManager.send(msg);
            Log.d("CAN_SEND",msg.toString());
            Log.d("CAN_SEND",bytesToHex(charge_byte));
        }

    }
    TimerTask mTimerTask;
    Timer timer;
    final Handler handler = new Handler();

    public void doTimerTask(){
        timer = new Timer();
        mTimerTask = new TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        send_charge();
                        Log.d("TIMER", "TimerTask run");
                    }
                });
            }};

        // public void schedule (TimerTask task, long delay, long period)
        timer.schedule(mTimerTask, CHARGE_DELAY, CHARGE_DELAY);  //

    }

    public void stopTimerTask(){

        if(mTimerTask!=null){
            Log.d("TIMER", "timer canceled");
            mTimerTask.cancel();
            timer = null;
        }

    }
    private VehicleMessage.Listener mListener = new VehicleMessage.Listener() {
        @Override
        public void receive(final VehicleMessage message) {
            Log.v("CAN_data", message.toString());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        number_voltage = (NumberPicker) findViewById(R.id.num_volts);
        number_amps = (NumberPicker) findViewById(R.id.num_amps);
        number_voltage_tenths = (NumberPicker) findViewById(R.id.num_volts_tenths);
        number_amps_tenths = (NumberPicker) findViewById(R.id.num_amps_tenths);
        button_charge = (ToggleButton) findViewById(R.id.button_charge);
        button_apply = (Button) findViewById(R.id.button_apply);
        switch_enable = (Switch) findViewById(R.id.switch_enable);
        switch_lock = (Switch) findViewById(R.id.switch_lock);
        slider_power = (SeekBar)  findViewById(R.id.seek_power);
        slider_power.setProgress(DEFAULT_POWER);
        text_power_percent = (TextView) findViewById(R.id.text_power_value);

        number_voltage_tenths.setMaxValue(9);
        number_voltage_tenths.setMinValue(0);
        number_voltage_tenths.setValue(DEFAULT_VOLTAGE_TENTHS);
        number_voltage_tenths.setWrapSelectorWheel(true);
        number_voltage_tenths.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        number_voltage_tenths.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int i, int i2) {
                if (i == 9 && i2 == 0) {
                    //was 9 now 0, increment
                    number_voltage.setValue(number_voltage.getValue() + 1);
                }
                if (i == 0 && i2 == 9) {
                    //was 9 now 0, decrement
                    number_voltage.setValue(number_voltage.getValue() - 1);
                }

            }
        });
        number_amps_tenths.setMaxValue(9);
        number_amps_tenths.setMinValue(0);
        number_amps_tenths.setValue(0);
        number_amps_tenths.setWrapSelectorWheel(true);
        number_amps_tenths.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        number_amps_tenths.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int i, int i2) {
                if (i == 9 && i2 == 0) {
                    //was 9 now 0, increase
                    number_amps.setValue(number_amps.getValue() + 1);
                }
                if (i == 0 && i2 == 9) {
                    //was 9 now 0, decrease
                    number_amps.setValue(number_amps.getValue() - 1);
                }

            }
        });

        button_charge.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                update_charger_data();
            }
        });
        button_apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast toast = Toast.makeText(getApplicationContext(), String.format("Applied Charge Settings: %d Volts, %d Amps",
                        number_voltage.getValue(), number_amps.getValue()), Toast.LENGTH_LONG);
                toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,50);
                toast.show();
                update_charger_data();
            }
        });

        switch_enable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(!b) {
                    switch_lock.setChecked(b);
                    button_charge.setChecked(b);
                    stopTimerTask();
                }
                else{
                    doTimerTask();
                }
                button_charge.setEnabled(b);
                text_power_percent.setEnabled(b);
                slider_power.setEnabled(b);
                button_apply.setEnabled(b);
                update_charger_data();
            }
        });

        switch_lock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                //button_charge.setEnabled(b);
                number_voltage.setEnabled(b);
                number_amps.setEnabled(b);
                number_voltage_tenths.setEnabled(b);
                number_amps_tenths.setEnabled(b);
            }
        });

        text_power_percent.setText(String.valueOf(slider_power.getProgress()));

        slider_power.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                text_power_percent.setText(String.valueOf(i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast toast = Toast.makeText(getApplicationContext(), String.format("Adjusting Charge Power to %d%%",
                        slider_power.getProgress()), Toast.LENGTH_LONG);
                toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,50);
                toast.show();
                //update_charger_data();
                byte[] pwr = convert(slider_power.getProgress(),0);
                charge_byte[C_BYTE_PWR_MSB]=pwr[MSB];
                charge_byte[C_BYTE_PWR_LSB]=pwr[LSB];
            }
        });
        number_voltage.setMaxValue(MAX_VOLTAGE);
        number_voltage.setMinValue(MIN_VOLTAGE);
        number_voltage.setValue(DEFAULT_VOLTAGE);
        number_voltage.setWrapSelectorWheel(false);
        number_voltage.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        number_voltage.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int i, int i2) {

            }
        });
        number_amps.setMaxValue(MAX_CURRENT);
        number_amps.setMinValue(MIN_CURRENT);
        number_amps.setValue(DEFAULT_CURRENT);
        number_amps.setWrapSelectorWheel(false);
        number_amps.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        number_amps.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int i, int i2) {

            }
        });
        progress_charge = (ProgressBar) findViewById(R.id.progress_charge);

        button_charge.setEnabled(false);
        button_apply.setEnabled(false);
        text_power_percent.setEnabled(false);
        number_voltage.setEnabled(false);
        number_amps_tenths.setEnabled(false);
        number_voltage_tenths.setEnabled(false);
        number_amps.setEnabled(false);
        progress_charge.setEnabled(false);
        slider_power.setEnabled(false);
    }

    private void update_charger_data() {
        charge_byte = charge(number_voltage.getValue(),number_voltage_tenths.getValue(),
                number_amps.getValue(),number_amps_tenths.getValue(),
                slider_power.getProgress(),button_charge.isChecked());
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onPause() {
        super.onPause();
        stopTimerTask();
        // When the activity goes into the background or exits, we want to make
        // sure to unbind from the service to avoid leaking memory
        if(mVehicleManager != null) {
            Log.i(TAG, "Unbinding from Vehicle Manager");
            // Remember to remove your listeners, in typical Android
            // fashion.
            mVehicleManager.removeListener(VehicleMessage.class,mListener);
            unbindService(mConnection);
            mVehicleManager = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // When the activity starts up or returns from the background,
        // re-connect to the VehicleManager so we can receive updates.
        if(mVehicleManager == null) {
            Intent intent = new Intent(this, VehicleManager.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }
}
