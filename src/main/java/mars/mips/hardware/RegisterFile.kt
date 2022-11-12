package mars.mips.hardware

import mars.Globals
import mars.assembler.SymbolTable
import mars.mips.instructions.Instruction
import mars.util.Binary
import java.util.*

/*
Copyright (c) 2003-2008,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
 */
/**
 * Represents the collection of MIPS registers.
 *
 * @author Jason Bumgarner, Jason Shrewsbury
 * @version June 2003
 */
object RegisterFile
{
    const val GLOBAL_POINTER_REGISTER = 28
    const val STACK_POINTER_REGISTER = 29

    /**
     * For returning the set of registers.
     *
     * @return The set of registers.
     */
    @JvmStatic
    val registers = arrayOf(
        Register("\$zero", 0, 0), Register("\$at", 1, 0),
        Register("\$v0", 2, 0), Register("\$v1", 3, 0),
        Register("\$a0", 4, 0), Register("\$a1", 5, 0),
        Register("\$a2", 6, 0), Register("\$a3", 7, 0),
        Register("\$t0", 8, 0), Register("\$t1", 9, 0),
        Register("\$t2", 10, 0), Register("\$t3", 11, 0),
        Register("\$t4", 12, 0), Register("\$t5", 13, 0),
        Register("\$t6", 14, 0), Register("\$t7", 15, 0),
        Register("\$s0", 16, 0), Register("\$s1", 17, 0),
        Register("\$s2", 18, 0), Register("\$s3", 19, 0),
        Register("\$s4", 20, 0), Register("\$s5", 21, 0),
        Register("\$s6", 22, 0), Register("\$s7", 23, 0),
        Register("\$t8", 24, 0), Register("\$t9", 25, 0),
        Register("\$k0", 26, 0), Register("\$k1", 27, 0),
        Register("\$gp", GLOBAL_POINTER_REGISTER, Memory.globalPointer),
        Register("\$sp", STACK_POINTER_REGISTER, Memory.stackPointer),
        Register("\$fp", 30, 0), Register("\$ra", 31, 0),  // Internal registers
        Register("pc", 32, Memory.textBaseAddress),
        Register("hi", 33, 0),
        Register("lo", 34, 0)
    )

    val registerNameIndex = registers.associateBy { it.name }

    @JvmStatic
    val programCounterRegister = registers[32]
    @JvmStatic
    private val HI = registers[33]
    @JvmStatic
    private val LO = registers[34]

    val backstep by lazy { Globals.getSettings().backSteppingEnabled }

    /**
     * Method for displaying the register values for debugging.
     */
    @JvmStatic
    fun showRegisters()
    {
        for (register in registers)
        {
            println("Name: " + register.name)
            println("Number: " + register.number)
            println("Value: " + register.value)
            println()
        }
    }

    /**
     * This method updates the register value who's number is num.  Also handles the lo and hi registers
     *
     * @param num Register to set the value of.
     * @param value The desired value for the register.
     */
    @JvmStatic
    fun updateRegister(num: Int, value: Int): Int
    {
        val old = 0
        if (num == 0)
        {
            println("You can not change the value of the zero register.")
        }
        else
        {
            registers[num].value = value
        }
        return old
    }

    /**
     * Sets the value of the register given to the value given.
     *
     * @param reg Name of register to set the value of.
     * @param value The desired value for the register.
     */
    @JvmStatic
    fun updateRegister(reg: String, value: Int)
    {
        registerNameIndex[reg]?.let {
            updateRegister(it.number, value)
        }
    }

    /**
     * Returns the value of the register who's number is num.
     *
     * @param num The register number.
     * @return The value of the given register.
     */
    @JvmStatic
    fun getValue(num: Int) = registers[num].value

    /**
     * For getting the number representation of the register.
     *
     * @param n The string formatted register name to look for.
     * @return The number of the register represented by the string or -1 if no match.
     */
    @JvmStatic
    fun getNumber(n: String) = registerNameIndex[n]?.number ?: -1

    /**
     * Get register object corresponding to given name.  If no match, return null.
     *
     * @param Rname The register name, either in $0 or $zero format.
     * @return The register object,or null if not found.
     */
    @JvmStatic
    fun getUserRegister(Rname: String): Register?
    {
        var reg: Register? = null
        if (Rname[0] == '$')
        {
            try
            {
                // check for register number 0-31.
                reg = registers[Binary.stringToInt(Rname.substring(1))] // KENV 1/6/05
            } catch (e: Exception)
            {
                // handles both NumberFormat and ArrayIndexOutOfBounds
                // check for register mnemonic $zero thru $ra
                reg = null // just to be sure
                // just do linear search; there aren't that many registers
                var i = 0
                while (i < registers.size)
                {
                    if (Rname == registers[i].name)
                    {
                        reg = registers[i]
                        break
                    }
                    i++
                }
            }
        }
        return reg
    }

    /**
     * For initializing the Program Counter.  Do not use this to implement jumps and branches, as it will NOT record a
     * backstep entry with the restore value. If you need backstepping capability, use setProgramCounter instead.
     *
     * @param value The value to set the Program Counter to.
     */
    @JvmStatic
    fun initializeProgramCounter(value: Int)
    {
        programCounterRegister.value = value
    }

    /**
     * Will initialize the Program Counter to either the default reset value, or the address associated with source
     * program global label "main", if it exists as a text segment label and the global setting is set.
     *
     * @param startAtMain If true, will set program counter to address of statement labeled 'main' (or other defined
     * start label) if defined.  If not defined, or if parameter false, will set program counter to default reset
     * value.
     */
    fun initializeProgramCounter(startAtMain: Boolean)
    {
        val mainAddr = Globals.symbolTable.getAddress(SymbolTable.getStartLabel())
        if (startAtMain && mainAddr != SymbolTable.NOT_FOUND && (Memory.inTextSegment(mainAddr) || Memory.inKernelTextSegment(
                mainAddr
            ))
        )
        {
            initializeProgramCounter(mainAddr)
        } else
        {
            initializeProgramCounter(programCounterRegister.resetValue)
        }
    }

    /**
     * For setting the Program Counter.  Note that ordinary PC update should be done using incrementPC() method. Use
     * this only when processing jumps and branches.
     *
     * @param value The value to set the Program Counter to.
     * @return previous PC value
     */
    @JvmStatic
    fun setProgramCounter(value: Int): Int
    {
        val old = programCounterRegister.value
        programCounterRegister.value = value
        if (Globals.getSettings().backSteppingEnabled)
        {
            Globals.program.backStepper.addPCRestore(old)
        }
        return old
    }

    /**
     * For returning the program counters value.
     *
     * @return The program counters value as an int.
     */
    @JvmStatic
    val pc: Int
        get() = programCounterRegister.value

    /**
     * For returning the program counter's initial (reset) value.
     *
     * @return The program counter's initial value
     */
    val initialProgramCounter: Int
        get() = programCounterRegister.resetValue

    /**
     * Method to reinitialize the values of the registers.
     * **NOTE:** Should *not* be called from command-mode MARS because this
     * this method uses global settings from the registry.  Command-mode must operate using only the command switches,
     * not registry settings.  It can be called from tools running stand-alone, and this is done in
     * `AbstractMarsToolAndApplication`.
     */
    @JvmStatic
    fun resetRegisters()
    {
        for (i in registers.indices)
        {
            registers[i].resetValue()
        }
        initializeProgramCounter(Globals.getSettings().startAtMain) // replaces "programCounter.resetValue()", DPS 3/3/09
        HI.resetValue()
        LO.resetValue()
    }

    /**
     * Method to increment the Program counter in the general case (not a jump or branch).
     */
    @JvmStatic
    fun incrementPC()
    {
        programCounterRegister.value += Instruction.INSTRUCTION_LENGTH
    }

    /**
     * Each individual register is a separate object and Observable.  This handy method will add the given Observer to
     * each one.  Currently does not apply to Program Counter.
     */
    @JvmStatic
    fun addRegistersObserver(observer: Observer?)
    {
        registers.filter { it.number != 32 }.forEach { it.addObserver(observer) }
    }

    /**
     * Each individual register is a separate object and Observable.  This handy method will delete the given Observer
     * from each one.  Currently does not apply to Program Counter.
     */
    @JvmStatic
    fun deleteRegistersObserver(observer: Observer?)
    {
        registers.filter { it.number != 32 }.forEach { it.deleteObserver(observer) }
    }
}
