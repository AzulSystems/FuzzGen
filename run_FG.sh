#!/bin/bash

source ${FUZZGEN_ROOT}/env.sh

function Save_test {
    cd `dirname $2`
    PWD=`pwd`
    echo "==== Save_test $PWD"
    mkdir -p $1
    chmod 775 $1 2>&1 >> /dev/null
    mv $2 $1 ## > /dev/null 2>&1
    echo "    $3!"
}

########################

function Compile_scala {

    export JAVA_HOME=${JAVA_REF_HOME}
    export JAVA_OPTS=${JAVA_REF_OPTS}
    ${SCALA_HOME}/bin/scalac ${ROOT}/${dir}/Test.scala > ${ROOT}/${dir}/scalac_Test.log 2>&1
    if [ $? -ne 0 ]; then
        Save_test invalids ${ROOT}/${dir} "Invalid Scala test generated: failed to compile with scalac"
        let invalids=invalids+1
        run_res=1
    fi

}

function Run_ref_scala {

    echo "Running test on reference Java"
    timeout -s 15 $TIME_OUT_REF ${SCALA_HOME}/bin/scala -cp ${ROOT}/${dir} Test > ${ROOT}/${dir}/out_ref.log 2>${ROOT}/${dir}/err_ref.log
    res=$?
    echo "exit code on reference Java: $res"
    if [ $res -eq 124 ]; then
## timeout does not work for scala, there are two processes, but only one of them got killed
        echo "REF HANG"
        nprc=` ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | wc -l`
        echo "$nprc"
        if [ $nprc -gt 0 ]; then
            ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | xargs kill -9
        fi
        echo "killed REF HANG"
####
        Save_test ref_hangs ${ROOT}/${dir} "Timeout on the reference Java $JAVA_HOME"
        return 1
    elif [ $res -eq 137 ]; then
        echo "REF OOM"
        nprc=` ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | wc -l`
        echo "$nprc"
        if [ $nprc -gt 0 ]; then
            ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | xargs kill -9
        fi
        echo "killed REF OOM"
        Save_test ref_OOM ${ROOT}/${dir} "OOM on the reference Java $JAVA_HOME"
        return 1
    elif [ $res -ne 0 ]; then
        echo "REF_FAILURE"
        nprc=` ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | wc -l`
        echo "$nprc"
        if [ $nprc -gt 0 ]; then
            ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | xargs kill -9
        fi
        echo "killed REF_FAILURE"
        Save_test ref_fails ${ROOT}/${dir} "Test failed on the reference Java $JAVA_HOME"
        return 1
#        elif TBD - separate crashes and failures
    fi

}

function Run_scala {

    echo "Running test on Java under test"

    echo "#!/bin/bash" >${run_sh_file}
    echo "export RESULTS_DIR=\${RESULTS_DIR:-\".\"}" >>${run_sh_file}
    echo "export TEST_DIR=\"\$(dirname \$(readlink -f \"\$0\"))\"" >>${run_sh_file}
    echo "JAVA_UNDER_TEST=\"\${JAVA_UNDER_TEST:-\$1}\"" >>${run_sh_file}
    echo "export JAVA_UNDER_TEST=\"\${JAVA_UNDER_TEST:-\"${JAVA_UNDER_TEST}\"}\""  >>${run_sh_file}
    echo "export JAVA_UNDER_TEST_HOME=\`dirname \\\`dirname \${JAVA_UNDER_TEST}\\\`\`" >>${run_sh_file}
    echo "export JAVA_HOME=\$JAVA_UNDER_TEST_HOME" >>${run_sh_file}
    echo "export JAVA_OPTS=\"\${JAVA_OPTS:-\"${JAVA_UNDER_TEST_OPTS} \"}\""  >>${run_sh_file}
    echo "export SCALA_HOME="\${SCALA_HOME:-"/home/qatest/sw/scala/scala-2.13.0"}"" >>${run_sh_file}
    echo "echo \"==== SCALA_HOME = \${SCALA_HOME}\""  >>${run_sh_file}
    echo "echo \"==== JAVA_HOME = \${JAVA_HOME} \""  >>${run_sh_file}
    echo "echo \"==== JAVA_OPTS = \${JAVA_OPTS} \""  >>${run_sh_file}

    echo "\${SCALA_HOME}/bin/scala -cp . Test > out_rerun.log 2>err_rerun.log"  >>${run_sh_file}

    echo "diff -I \".*VM option.*\" out_ref.log out_rerun.log"  >>${run_sh_file}
    echo "res_out=\$?" >>${run_sh_file}
    echo "diff -I \".*VM option.*\" err_ref.log err_rerun.log"  >>${run_sh_file}
    echo "res_err=\$?"  >>${run_sh_file}

    echo "sum=\$((res_out + res_err))"  >>${run_sh_file}
    echo "[[ \$sum -eq 0 ]] || { echo \"FAILED\"; exit 1; }"  >>${run_sh_file}
    echo "echo \"PASSED\""  >>${run_sh_file}

    chmod 775  ${run_sh_file}

    export JAVA_HOME=$JAVA_UNDER_TEST_HOME
    echo "==== JAVA_HOME=${JAVA_HOME}"
    export JAVA_OPTS="${JAVA_UNDER_TEST_OPTS} -XX:LogFile=${ROOT}/${dir}/Compilation.log"
    echo " === JAVA_OPTS=$JAVA_OPTS"
    timeout -s 15 $TIME_OUT ${SCALA_HOME}/bin/scala -cp ${ROOT}/${dir} Test > ${ROOT}/${dir}/out.log 2>${ROOT}/${dir}/err.log
    res=$?
    echo "exit code on Java under test: $res"
    if [ $res -eq 124 ]; then
        echo "HANG"
        nprc=` ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | wc -l`
        echo "$nprc"
        if [ $nprc -gt 0 ]; then
            ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | xargs kill -9
        fi
        echo "killed HANG"
        Save_test hangs ${ROOT}/${dir} "Timeout on Java $JAVA_HOME"
    elif [ $res -eq 137 ]; then
        echo "OOM"
        nprc=` ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | wc -l`
        echo "$nprc"
        if [ $nprc -gt 0 ]; then
            ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | xargs kill -9
        fi
        echo killed "OOM"
        Save_test OOM ${ROOT}/${dir} "OOM on Java $JAVA_HOME"
    elif [ $res -ne 0 ]; then
        echo "CRASH"
        nprc=` ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | wc -l`
        echo "$nprc"
        if [ $nprc -gt 0 ]; then
            ps ax | grep scala | grep Test | grep ${GRAMMAR}_${i} | grep ${dir} |awk '{print $1}' | xargs kill -9
        fi
        echo "killed CRASH"
        Save_test crashes ${ROOT}/${dir} "Test CRASHED on Java $JAVA_HOME"
#        elif TBD - to separate crashes and failures
### compare results
###
    elif diff  -I ".*VM option.*" out.log out_ref.log > out.diff 2>&1 && diff err.log err_ref.log > err.diff 2>&1; then
        Save_test passed ${ROOT}/${dir} "Test PASSED on Java $JAVA_HOME"
    else
        Save_test fails ${ROOT}/${dir} "Test FAILED on Java $JAVA_HOME"
    fi

}

########################

function Compile_java {

    ${JAVA_REF_HOME}/bin/javac ${JAVA_REF_OPTS} ${ROOT}/${dir}/Test.java > ${ROOT}/${dir}/javac_Test.log 2>&1
    if [ $? -ne 0 ]; then
        Save_test invalids ${ROOT}/${dir} "Invalid Java test generated: failed to compile with javac"
        let invalids=invalids+1
        return 1
    fi
    return 0

}

function Run_ref_java {

    echo "Running test on reference Java"
    timeout -s 15 $TIME_OUT_REF ${JAVA_REF_HOME}/bin/java -cp ${ROOT}/${dir} Test > ${ROOT}/${dir}/out_ref.log 2>${ROOT}/${dir}/err_ref.log
    res=$?
    echo "exit code on reference Java: $res"
    if [ $res -eq 124 ]; then
        echo "REF HANG"
        Save_test ref_hangs ${ROOT}/${dir} "Timeout on the reference Java $JAVA_HOME"
        return 1
    elif [ $res -eq 137 ]; then
        echo "REF OOM"
        Save_test ref_OOM ${ROOT}/${dir} "OOM on the reference Java $JAVA_HOME"
        return 1
    elif [ $res -ne 0 ]; then
        echo "REF_FAILURE"
        Save_test ref_fails ${ROOT}/${dir} "Test failed on the reference Java $JAVA_HOME"
        return 1
#    elif TBD - separate crashes and failures
    fi
    return 0

}

function Run_java {

    echo "Running test on Java under test"

    echo "#!/bin/bash" >${run_sh_file}
    echo "export RESULTS_DIR=\${RESULTS_DIR:-\".\"}" >>${run_sh_file}
    echo "export TEST_DIR=\"\$(dirname \$(readlink -f \"\$0\"))\"" >>${run_sh_file}
    echo "JAVA_UNDER_TEST=\"\${JAVA_UNDER_TEST:-\$1}\"" >>${run_sh_file}
    echo "export JAVA_UNDER_TEST=\"\${JAVA_UNDER_TEST:-\"${JAVA_UNDER_TEST}\"}\""  >>${run_sh_file}
    echo "export JAVA_UNDER_TEST_OPTS=\"\${JAVA_UNDER_TEST_OPTS:-\"${JAVA_UNDER_TEST_OPTS} \"}\""  >>${run_sh_file}

    echo "\${JAVA_UNDER_TEST} \${JAVA_UNDER_TEST_OPTS} -cp . Test > out_rerun.log 2>err_rerun.log"  >>${run_sh_file}

    echo "diff -I \".*VM option.*\" out_ref.log out_rerun.log"  >>${run_sh_file}
    echo "res_out=\$?" >>${run_sh_file}
    echo "diff -I \".*VM option.*\" err_ref.log err_rerun.log"  >>${run_sh_file}
    echo "res_err=$?"  >>${run_sh_file}

    echo "sum=\$((res_out + res_err))"  >>${run_sh_file}
    echo "[[ \$sum -eq 0 ]] || { echo \"FAILED\"; exit 1; }"  >>${run_sh_file}
    echo "echo \"PASSED\""  >>${run_sh_file}

    chmod 775  ${run_sh_file}

    timeout -s 15 $TIME_OUT ${JAVA_UNDER_TEST} ${JAVA_UNDER_TEST_OPTS} -cp ${ROOT}/${dir} Test > ${ROOT}/${dir}/out.log 2>${ROOT}/${dir}/err.log
    res=$?
    echo "exit code on Java under test: $res"
    if [ $res -eq 124 ]; then
        echo "HANG"
        Save_test hangs ${ROOT}/${dir} "Timeout on Java $JAVA_UNDER_TEST"
    elif [ $res -eq 137 ]; then
        echo "OOM"
        Save_test OOM ${ROOT}/${dir} "OOM on Java $JAVA_UNDER_TEST"
    elif [ $res -ne 0 ]; then
        echo "CRASH"
        Save_test crashes ${ROOT}/${dir} "Test CRASHED on Java $JAVA_UNDER_TEST"
#    elif TBD - to separate crashes and failures
### compare results
###
    elif diff  -I ".*VM option.*" out.log out_ref.log > out.diff 2>&1 && diff err.log err_ref.log > err.diff 2>&1; then
        Save_test passed ${ROOT}/${dir} "Test PASSED on Java $JAVA_UNDER_TEST"
    else
        Save_test fails ${ROOT}/${dir} "Test FAILED on Java $JAVA_UNDER_TEST"
    fi

}
 
########################

RESULTS_DIR=$1
TIMESTAMP=$2
i=$3
seed=$4

run_sh_file=run.sh

mkdir ${RESULTS_DIR}/${GRAMMAR}_${i}
###
echo "Running test generator ..."
###

${JAVA_REF_HOME}/bin/java -cp ${FUZZGEN_ROOT}/FuzzGen.jar:${FUZZGEN_ROOT}/scala-library-2.13.5.jar  com.azul.fuzzgen.FuzzGen --verbose --parallel --grammar=${FUZZGEN_CONFIGS_ROOT}/${GRAMMAR} --output=${RESULTS_DIR}/${GRAMMAR}_${i} --seed=$seed --num-tests=${NTESTS} --num-attempts=${NATTEMPTS} > ${RESULTS_DIR}/${GRAMMAR}_${i}/generate_${BUILD}_${TIMESTAMP}_${i}.log 2>&1

case ${WHAT} in
    scala)
        compile_func="Compile_scala"
        run_ref_func="Run_ref_scala"
        run_func="Run_scala"
        sffx="scala"
        ;;

    java)
        compile_func="Compile_java"
        run_ref_func="Run_ref_java"
        run_func="Run_java"
        sffx="java"
        ;;

    *) echo "Unsupported language"
       exit
        ;;
esac

### distribute tests for separate dirs
cd ${RESULTS_DIR}/${GRAMMAR}_${i}
ROOT=`pwd`
for gen in `ls -1 | grep gen_`; do echo ${gen}; dir="${BUILD}-${JDK}-${i}-${gen:4:3}"; mkdir ${dir}; mv ${gen} ${dir}/Test.${sffx}; done > distribute_$TIMESTAMP.log 2>&1
###

invalids=0
run_res=0 

for dir in `ls -1 | grep -v log`; do 

    echo ${dir}; cd ${dir};
### compile test
    
    eval ${compile_func}
    run_res=$?
    if [[ ${run_res} -ne 0 ]]; then 
        continue
    fi

### run tests on reference java

    eval ${run_ref_func}
    run_res=$?
    if [[ ${run_res} -ne 0 ]]; then
        continue
    fi

### run tests on Falcon

     eval ${run_func}

done
cd ../../

