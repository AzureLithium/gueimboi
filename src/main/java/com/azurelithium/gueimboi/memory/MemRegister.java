package com.azurelithium.gueimboi.memory;

import com.azurelithium.gueimboi.common.GameBoy;
import com.azurelithium.gueimboi.dma.DMAController;
import com.azurelithium.gueimboi.gpu.GPU;
import com.azurelithium.gueimboi.gui.Display;
import com.azurelithium.gueimboi.joypad.Input;
import com.azurelithium.gueimboi.joypad.InputController;
import com.azurelithium.gueimboi.timer.Timer;
import com.azurelithium.gueimboi.utils.ByteUtils;

class MemRegister {

    protected Memory memory;
    protected int memRegisterAddress;

    protected MemRegister(Memory _memory, int _memRegisterAddress) {
        memory = _memory;
        memRegisterAddress = _memRegisterAddress;
    }

    int getAddress() {
        return memRegisterAddress;
    }

    int read() {
        return memory.readByte(memRegisterAddress) & 0xFF;
    }

    int controlledRead() {
        return read();
    }

    void write(int value) {
        memory.writeByte(memRegisterAddress, value & 0xFF);
    }

    void controlledWrite(int value) {
        write(value);
    }
}


class JOYP extends MemRegister {

    private InputController joypad;

    JOYP(Memory _memory, int _memRegisterAddress, InputController _joypad) {
        super(_memory, _memRegisterAddress);
        joypad = _joypad;
    }

    int read() {
        return super.read() | 0xC0;
    }

    void write(int value) {
        super.write(value | 0xC0);
    }

    int controlledRead() {
        int JOYP = read() | 0xF;
        for (Input i : joypad.getPressedInputs()) {
            if (!ByteUtils.getBit(JOYP, i.getSelectBit())) {
                JOYP = ByteUtils.resetBit(JOYP, i.getPressedBit());
            }
        }
        return JOYP;
    }

    protected void controlledWrite(int value) {
        int JOYP = read();
        write((JOYP & 0xCF) | (value & 0x30));
    }

}


class DIV extends MemRegister {

    private Timer timer;

    DIV(Memory _memory, int _memRegisterAddress, Timer _timer) {
        super(_memory, _memRegisterAddress);
        timer = _timer;
    }

    void controlledWrite(int value) {
        timer.checkTIMAUnexpectedIncrease();
        memory.writeByte(memRegisterAddress - 1, 0);
        memory.writeByte(memRegisterAddress, 0);
    }

}


class DIVLSB extends MemRegister {

    private Timer timer;

    DIVLSB(Memory _memory, int _memRegisterAddress, Timer _timer) {
        super(_memory, _memRegisterAddress);
        timer = _timer;
    }

    void controlledWrite(int value) {
        timer.checkTIMAUnexpectedIncrease();
        memory.writeByte(memRegisterAddress, 0);
        memory.writeByte(memRegisterAddress + 1, 0);
    }

}


class TIMA extends MemRegister {

    private Timer timer;

    TIMA(Memory _memory, int _memRegisterAddress, Timer _timer) {
        super(_memory, _memRegisterAddress);
        timer = _timer;
    }

    void controlledWrite(int value) {
        if (timer.getTicksSinceOverflow() < GameBoy.CYCLES_PER_M_CYCLE) {    // normal or in overflow
            write(value);
            timer.unsetOverflow();
        }
    }

}


class TMA extends MemRegister {

    private Timer timer;

    TMA(Memory _memory, int _memRegisterAddress, Timer _timer) {
        super(_memory, _memRegisterAddress);
        timer = _timer;
    }

    void controlledWrite(int value) {
        write(value);
        if (timer.getTicksSinceOverflow() >= GameBoy.CYCLES_PER_M_CYCLE) {   // just after overflow        
            timer.setTIMA(value);
        }
    }

}


class TAC extends MemRegister {

    private Timer timer;

    TAC(Memory _memory, int _memRegisterAddress, Timer _timer) {
        super(_memory, _memRegisterAddress);
        timer = _timer;
    }

    void controlledWrite(int value) {
        boolean oldTickRateBit = timer.isTimerEnabled() && timer.getTickRateBit();
        write(value);
        boolean newTickRateBit = timer.isTimerEnabled() && timer.getTickRateBit();
        if (oldTickRateBit && !newTickRateBit) {
            timer.checkTIMAUnexpectedIncrease();
        }
    }

}


class IF extends MemRegister {

    private Timer timer;

    IF(Memory _memory, int _memRegisterAddress, Timer _timer) {
        super(_memory, _memRegisterAddress);
        timer = _timer;
    }

    int read() {
        return super.read() | 0xE0;
    }

    void write(int value) {
        super.write(value | 0xE0);
    }

    void controlledWrite(int value) {
        write(value);
        if (timer.getTicksSinceOverflow() >= GameBoy.CYCLES_PER_M_CYCLE) {   // just after overflow
            timer.setIFOverride();
        }
    }

}


class LCDC extends MemRegister {

    private GPU GPU;
    private Display display;

    LCDC(Memory _memory, int _memRegisterAddress, GPU _GPU, Display _display) {
        super(_memory, _memRegisterAddress);
        GPU = _GPU;
        display = _display;
    }

    void controlledWrite(int value) {
        int LCDC = read();
        if ((LCDC & 0xC0) != 0 && (value & 0xC0) == 0) {
            GPU.reset();
            display.clear();
        }
        write(value);
    }

}


class STAT extends MemRegister {

    STAT(Memory _memory, int _memRegisterAddress) {
        super(_memory, _memRegisterAddress);
    }

    int read() {
        return super.read() | 0x80;
    }

    void write(int value) {
        super.write(value | 0x80);
    }

    void controlledWrite(int value) {
        int STAT = read();
        write((value & 0x78) | STAT);
    }

}


class DMA extends MemRegister {

    private DMAController DMAController;

    DMA(Memory memory, int memRegisterAddress, DMAController _DMAController) {
        super(memory, memRegisterAddress);
        DMAController = _DMAController;
    }

    void controlledWrite(int value) {
        if (value >= 0x00 && value <= 0xF1) { // supported range
            write(value);
            DMAController.startTransfer(value << Byte.SIZE);
        }
    }

}


class ROM_DISABLE extends MemRegister {

    private MMU MMU;

    ROM_DISABLE(Memory memory, int memRegisterAddress, MMU _MMU) {
        super(memory, memRegisterAddress);
        MMU = _MMU;
    }

    void controlledWrite(int value) {
        // bootrom disabling
        int ROM_DISABLE = read(); 
        if (ROM_DISABLE != 0x01 && value == 0x01) { 
            memory.writeByte(memRegisterAddress, value);
            MMU.loadCartridge();
        }
    }

}


class IE extends MemRegister {

    IE(Memory _memory, int _memRegisterAddress) {
        super(_memory, _memRegisterAddress);
    }

    int read() {
        return super.read() | 0xE0;
    }

    void write(int value) {
        super.write(value | 0xE0) ;
    }

}
