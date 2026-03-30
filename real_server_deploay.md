# opendataloader-pdf 운영환경 배포/설치 가이드

## 목적

`airun` 운영환경에서 `opendataloader-pdf`를 안정적으로 설치하고 사용하기 위한 배포 방식을 정리한다.

대상 경로:

- AIRUN 배포 루트: `/home/gon/work/airun_proj/deploy/airun`
- 운영 Python venv: `~/.airun_venv`
- 코어 설치 스크립트: `/home/gon/work/airun_proj/deploy/airun/install_scripts/install-core.sh`

이 문서는 특히 다음 조건을 만족하는 방식을 기준으로 한다.

- 운영환경에서 반복 설치 가능
- 폐쇄망 환경에서도 설치 가능
- PyPI 접속 없이도 설치 가능
- `opendataloader-pdf`의 JAR 포함 wheel을 그대로 사용

## 권장 방식

가장 적합한 방식은 `opendataloader-pdf`를 사전 빌드한 wheel로 배포본에 포함시키고, `install-core.sh`에서 `~/.airun_venv`에 로컬 파일로 설치하는 것이다.

권장 이유:

- 폐쇄망에서도 설치 가능
- 외부 Python 인덱스 의존성 제거
- wheel 내부에 JAR가 포함되어 있어 Python 패키지 1개 설치로 완료 가능
- 운영 배포 버전을 명시적으로 고정 가능
- 장애 시 설치 대상을 추적하기 쉽다

## 비권장 방식

`requirements-linux.txt`에 `opendataloader-pdf`를 직접 추가해서 PyPI에서 설치하는 방식은 운영환경, 특히 폐쇄망 환경에 적합하지 않다.

이유:

- 외부 네트워크 의존성 발생
- 사내 빌드본/커스텀 수정본 반영이 어려움
- 배포 재현성이 떨어짐

## 사전 준비

### 1. wheel 생성

개발/빌드 머신에서 `opendataloader-pdf` wheel을 생성한다.

```bash
cd /home/gon/work/ttt4/dataloader
./scripts/build-all.sh 0.0.0 --no-install
```

생성 결과 예시:

```text
/home/gon/work/ttt4/dataloader/python/opendataloader-pdf/dist/opendataloader_pdf-0.0.0-py3-none-any.whl
```

### 2. 배포본에 wheel 포함

배포 저장소에 wheel 보관 디렉터리를 만든다.

권장 위치:

```text
/home/gon/work/airun_proj/deploy/airun/install_files/python-wheels/
```

복사:

```bash
mkdir -p /home/gon/work/airun_proj/deploy/airun/install_files/python-wheels
cp /home/gon/work/ttt4/dataloader/python/opendataloader-pdf/dist/opendataloader_pdf-0.0.0-py3-none-any.whl \
  /home/gon/work/airun_proj/deploy/airun/install_files/python-wheels/
```

## 운영 설치 시 필요한 시스템 패키지

`opendataloader-pdf`는 Java 런타임이 필요하다.

`install-core.sh`의 시스템 패키지 목록에 아래 중 하나를 포함하는 것을 권장한다.

권장:

```text
openjdk-17-jre-headless
```

대안:

```text
default-jre-headless
```

현재 `install-core.sh`의 패키지 목록에는 Java 런타임이 없다. 따라서 운영환경에서 `java`가 기본 설치되어 있지 않다면 반드시 추가해야 한다.

수정 위치:

- [`install-core.sh`](/home/gon/work/airun_proj/deploy/airun/install_scripts/install-core.sh)

예시:

```bash
PACKAGES=(
    "build-essential"
    "python3-dev"
    "python3-pip"
    "python3-venv"
    "git"
    "curl"
    "wget"
    "redis-server"
    "postgresql-client"
    "libpq-dev"
    "tesseract-ocr"
    "tesseract-ocr-kor"
    "poppler-utils"
    "libmagic1"
    "ffmpeg"
    "libssl-dev"
    "libffi-dev"
    "libxml2-dev"
    "libxslt1-dev"
    "libjpeg-dev"
    "zlib1g-dev"
    "openjdk-17-jre-headless"
)
```

## install-core.sh 반영 방법

`install-core.sh`는 현재 다음 방식으로 venv를 만들고 requirements를 설치한다.

```bash
"${VENV_PATH}/bin/pip" install --no-cache-dir --prefer-binary -r "${CURRENT_DIR}/requirements-linux.txt"
```

이 직후에 `opendataloader-pdf` wheel 설치를 추가하는 것이 가장 안전하다.

### 권장 추가 코드

```bash
ODL_WHEEL="${CURRENT_DIR}/install_files/python-wheels/opendataloader_pdf-0.0.0-py3-none-any.whl"

if [ -f "$ODL_WHEEL" ]; then
    show_info "opendataloader-pdf wheel 설치 중..."
    "${VENV_PATH}/bin/pip" install --no-cache-dir "$ODL_WHEEL" || \
        handle_error "opendataloader-pdf 설치 실패"
    show_success "opendataloader-pdf 설치 완료"
else
    show_warning "opendataloader-pdf wheel 파일이 없어 설치를 건너뜁니다: $ODL_WHEEL"
fi
```

### 삽입 권장 위치

`install-core.sh`의 아래 블록 직후:

```bash
"${VENV_PATH}/bin/pip" install --no-cache-dir --prefer-binary -r "${CURRENT_DIR}/requirements-linux.txt" || \
    handle_error "Python 패키지 설치 실패"
```

## 온라인 환경 설치 절차

1. 개발 머신에서 wheel 생성
2. 배포본의 `install_files/python-wheels/`에 wheel 포함
3. `install-core.sh`에 로컬 wheel 설치 코드 반영
4. 운영 서버에서 설치 실행

```bash
cd /home/gon/work/airun_proj/deploy/airun
./install.sh
```

또는 코어만 직접:

```bash
cd /home/gon/work/airun_proj/deploy/airun
./install_scripts/install-core.sh
```

## 폐쇄망 환경 설치 절차

폐쇄망에서는 반드시 wheel을 배포본에 포함해야 한다.

추가로 시스템 패키지와 Python wheel 오프라인 저장소도 함께 준비해야 한다.

현재 배포본에는 오프라인 관련 스크립트가 이미 존재한다.

- `/home/gon/work/airun_proj/deploy/airun/scripts/prepare-offline-package.sh`
- `/home/gon/work/airun_proj/deploy/airun/scripts/install-offline.sh`

### 권장 절차

1. 인터넷 가능한 빌드 머신에서 `opendataloader-pdf` wheel 생성
2. offline package 생성 전에 아래 파일을 포함

```text
install_files/python-wheels/opendataloader_pdf-0.0.0-py3-none-any.whl
```

3. 오프라인 패키지 생성
4. 폐쇄망 서버로 패키지 이관
5. 폐쇄망 서버에서 `install-offline.sh` 실행

### 폐쇄망에서 중요한 점

- `requirements-linux.txt`만으로는 충분하지 않다
- `opendataloader-pdf`는 반드시 로컬 wheel 파일이 있어야 한다
- Java 런타임용 `.deb` 패키지도 오프라인 저장소에 포함되어야 한다

즉, 폐쇄망용 패키지에는 최소한 아래가 모두 있어야 한다.

- Python wheels
- `opendataloader-pdf` wheel
- Java runtime package
- 기존 AIRUN 오프라인 의존 패키지

## 설치 확인 방법

운영 서버에서 아래로 확인한다.

### 1. Python 패키지 확인

```bash
~/.airun_venv/bin/pip show opendataloader-pdf
```

### 2. import 확인

```bash
~/.airun_venv/bin/python -c "import opendataloader_pdf; print(opendataloader_pdf.__file__)"
```

### 3. Java 확인

```bash
java -version
```

### 4. 실제 실행 확인

```bash
~/.airun_venv/bin/python -c "import opendataloader_pdf; print('ok')"
```

## 운영 반영 후 확인 포인트

`airun`에서 문서 임베딩 수행 후 다음을 확인한다.

- `opendataloader-pdf` import 실패가 없는지
- `java` 관련 에러가 없는지
- `.extracts/<문서명>/`에 `.md`, `.json` 생성되는지
- `.extracts/<문서명>_images/`에 이미지 생성되는지
- 이미지 설명 옵션 사용 시 JSON의 `description`이 채워지는지

## 최종 권장안 요약

운영환경 배포 방식은 아래로 고정하는 것을 권장한다.

1. `opendataloader-pdf`는 개발 머신에서 wheel로 빌드한다.
2. wheel을 `deploy/airun/install_files/python-wheels/`에 포함한다.
3. `install-core.sh`에서 `requirements-linux.txt` 설치 후 로컬 wheel을 `~/.airun_venv`에 설치한다.
4. `openjdk-17-jre-headless`를 시스템 패키지에 포함한다.
5. 폐쇄망은 동일 방식으로 wheel과 Java 패키지를 오프라인 묶음에 포함한다.

이 방식이 현재 구조에서 가장 단순하고, 운영/폐쇄망 모두에 재현성이 높다.
