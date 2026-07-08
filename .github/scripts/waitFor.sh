#!/usr/bin/env bash
# usage:
# ./waitFor.sh joke operators Succeeded
# ./waitFor.sh pod operators Running "name -o jsonpath='{.status.phase}'"

RESOURCE="${1}"
SELECTOR="${2}"
KUBE_NAMESPACE="${3}"
EXPECTED="${4}"
EXTRA="${5-}"
RETRIES="${RETRIES:-10}"
INTERVAL="${INTERVAL:-15}"

retries=$RETRIES
until [[ $retries == 0 ]]; do
  echo
  actual=$(kubectl get $RESOURCE $SELECTOR -n $KUBE_NAMESPACE $EXTRA 2>/dev/null || echo "Waiting for $RESOURCE/$SELECTOR in namespace $KUBE_NAMESPACE -> $EXPECTED to appear")
  if [[ "$actual" =~ .*"$EXPECTED".* ]]; then
    echo "Resource \"$RESOURCE/$SELECTOR\" found" 2>&1
    echo "$actual" 2>&1
    exit 0
  else
    echo "Waiting for resource \"$RESOURCE/$SELECTOR\" in namespace $KUBE_NAMESPACE ..." 2>&1
    echo "$actual" 2>&1
    kubectl describe $RESOURCE $SELECTOR -n $KUBE_NAMESPACE 2>/dev/null | grep -A 5 "Events:" || true
  fi
  sleep "${INTERVAL}s"
  retries=$((retries - 1))
done

exit 1